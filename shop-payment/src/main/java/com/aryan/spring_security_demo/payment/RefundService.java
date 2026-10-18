package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.order.OrderServiceInterface;
import com.aryan.spring_security_demo.order.RefundedItem;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An admin's partial refunds (docs/payment-design.md, section 11), and an order's
 * refunds as its owner or an admin sees them. Owing a refund is one short
 * transaction; the provider is called after it commits, by
 * {@link RefundAfterCommitListener}, as for a cancellation.
 */
@Service
@RequiredArgsConstructor
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderServiceInterface orderService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** The refund, and whether this request made it (or found it from an earlier one with the same key). */
    public record Outcome(RefundDto refund, boolean created) {
    }

    /**
     * Owe back part of a paid order: items, priced at what the customer paid for
     * them, or an amount. One transaction, holding the payment's row lock from the
     * start, so two refunds at once take turns and can't add up to more than was
     * paid; a cancellation takes the same lock.
     *
     * <p>The lock is the transaction's first read, before the order's. Under MySQL's
     * {@code REPEATABLE READ}, plain reads return the database as it was at the first
     * of them; taken after the lock, they include all that the refund before this one
     * committed: its earlier refunds' items, and its items' refunded quantities.
     *
     * @param requestKey the admin's {@code Idempotency-Key}: the same request again
     *                   returns the first refund
     */
    @Transactional
    public Outcome refund(Long orderId, String requestKey, RefundOrderRequest request, String requestedBy) {
        Optional<Payment> live = paymentRepository.findLiveByOrderForUpdate(orderId);
        orderService.getOrder(orderId);  // 404 if there is no such order
        Payment payment = live.orElseThrow(() -> new RefundNotPossibleException("The order isn't paid"));
        Optional<Refund> earlier = payment.refundRequestedAs(requestKey);
        if (earlier.isPresent()) {
            requireSameRequest(earlier.get(), request);
            return new Outcome(RefundDto.from(earlier.get()), false);
        }
        payment.requireRefundable();  // before any item is counted as refunded
        Instant now = Instant.now(clock);
        Refund refund;
        if (request.hasItems()) {
            List<RefundedItem> items = orderService.refundItems(orderId, quantities(request), request.isRestock());
            BigDecimal amount = items.stream().map(RefundedItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            refund = payment.refund(Refund.Kind.ITEMS, amount, request.getReason(), requestKey, requestedBy, now);
            items.forEach(item -> refund.addItem(item.orderItemId(), item.quantity(), item.amount()));
        } else {
            refund = payment.refund(Refund.Kind.AMOUNT, request.getAmount(), request.getReason(), requestKey,
                    requestedBy, now);
        }
        refundRepository.save(refund);
        // Sent once this commits, and never if it rolls back.
        events.publishEvent(new RefundsRequestedEvent(List.of(refund.getId())));
        return new Outcome(RefundDto.from(refund), true);
    }

    /** An order's refunds, oldest first, for its owner or an admin. */
    @Transactional(readOnly = true)
    public List<RefundDto> getRefunds(Long orderId) {
        orderService.getOrder(orderId);  // the owner or an admin, else 403; 404 if no such order
        return refundRepository.findAllByOrder(orderId).stream().map(RefundDto::from).toList();
    }

    /** One of an order's refunds, for its owner or an admin. */
    @Transactional(readOnly = true)
    public RefundDto getRefund(Long orderId, Long refundId) {
        return getRefunds(orderId).stream()
                .filter(refund -> refund.id().equals(refundId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("refund not found!"));
    }

    private static Map<Long, Integer> quantities(RefundOrderRequest request) {
        return request.getItems().stream()
                .collect(Collectors.toMap(RefundOrderRequest.RefundLine::getOrderItemId, RefundOrderRequest.RefundLine::getQuantity));
    }

    /** A key names one refund: the same key with another request is refused, not answered with the first. */
    private static void requireSameRequest(Refund earlier, RefundOrderRequest request) {
        boolean same = request.hasItems()
                ? earlier.getKind() == Refund.Kind.ITEMS && quantities(request).equals(earlier.getItems().stream()
                        .collect(Collectors.toMap(RefundItem::getOrderItemId, RefundItem::getQuantity)))
                : earlier.getKind() == Refund.Kind.AMOUNT && earlier.getAmount().compareTo(request.getAmount()) == 0;
        if (!same) {
            throw new IdempotencyKeyReusedException("This Idempotency-Key was already used for a different refund");
        }
    }
}
