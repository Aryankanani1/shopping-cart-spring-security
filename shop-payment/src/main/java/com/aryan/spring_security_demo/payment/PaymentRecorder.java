package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.InvalidOrderStateException;
import com.aryan.spring_security_demo.order.Order;
import com.aryan.spring_security_demo.order.OrderServiceInterface;
import com.aryan.spring_security_demo.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * The database steps of a payment, each a short transaction of its own: recording
 * an attempt before the provider is called, and its outcome after. A separate
 * bean from {@link PaymentService}, which calls the provider in between, so each
 * step goes through Spring's proxy and gets its transaction, and no transaction
 * is open while the provider answers (CLAUDE.md).
 */
@Service
@RequiredArgsConstructor
public class PaymentRecorder {

    /** Failed attempts allowed per order; see {@link TooManyPaymentAttemptsException}. */
    static final int MAX_FAILED_ATTEMPTS = 5;

    /** The shop sells in one currency; the client never chooses it. */
    static final String CURRENCY = "USD";

    private final PaymentRepository paymentRepository;
    private final OrderServiceInterface orderService;
    private final Clock clock;

    /**
     * Record a new attempt to pay for the order, or find the one this key already
     * started. The order stays locked until this commits, so attempts for one
     * order take turns: the second of two requests with the same key finds the
     * first's payment, and of two with different keys the second finds a payment
     * in progress or an order no longer pending.
     *
     * @return the attempt, and whether it was found rather than started
     */
    @Transactional
    public Attempt start(Long orderId, String idempotencyKey, String paymentMethod) {
        Order order = orderService.lockForPayment(orderId);
        // Before the status checks: a repeated request gets its first answer even
        // after that payment made the order PAID.
        Optional<Payment> earlier = paymentRepository.findByOrderAndKey(orderId, idempotencyKey);
        if (earlier.isPresent()) {
            Payment payment = earlier.get();
            if (!payment.getPaymentMethod().equals(paymentMethod)) {
                throw new IdempotencyKeyReusedException();
            }
            return new Attempt(PaymentDto.from(payment), true);
        }
        if (!order.getOrderStatus().canTransitionTo(OrderStatus.PAID)) {
            throw new InvalidOrderStateException(
                    "Only a pending order can be paid for; this one is " + order.getOrderStatus());
        }
        if (paymentRepository.countByOrderAndStatus(orderId, PaymentStatus.PROCESSING) > 0) {
            throw new PaymentInProgressException("A payment for this order is already being processed");
        }
        if (paymentRepository.countByOrderAndStatus(orderId, PaymentStatus.FAILED) >= MAX_FAILED_ATTEMPTS) {
            throw new TooManyPaymentAttemptsException(MAX_FAILED_ATTEMPTS);
        }
        orderService.startPayment(order);
        Payment payment = paymentRepository.save(
                Payment.start(order, idempotencyKey, paymentMethod, CURRENCY, Instant.now(clock)));
        return new Attempt(PaymentDto.from(payment), false);
    }

    /** The provider approved: the payment succeeded and the order is paid, together or not at all. */
    @Transactional
    public PaymentDto recordSuccess(Long paymentId, ChargeResult.Approved approved) {
        Payment payment = load(paymentId);
        payment.succeed(approved.providerReference(), approved.cardBrand(), approved.cardLast4(), Instant.now(clock));
        orderService.markPaid(payment.getOrder().getId());
        return PaymentDto.from(payment);
    }

    /** Nothing was charged; the order stays pending, and can be paid with another attempt. */
    @Transactional
    public PaymentDto recordFailure(Long paymentId, String reason) {
        Payment payment = load(paymentId);
        payment.fail(reason, Instant.now(clock));
        return PaymentDto.from(payment);
    }

    private Payment load(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment " + paymentId + " is missing"));
    }

    /** A payment attempt, and whether an earlier request with the same key already started it. */
    public record Attempt(PaymentDto payment, boolean repeated) {
    }
}
