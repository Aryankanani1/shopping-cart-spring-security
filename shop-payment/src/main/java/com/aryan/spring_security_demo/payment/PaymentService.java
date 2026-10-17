package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.order.OrderServiceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService implements PaymentServiceInterface {

    static final String PROVIDER_UNAVAILABLE = "The payment provider is unavailable; please try again in a moment";

    private final PaymentRecorder recorder;
    private final PaymentGatewayClient gateway;
    private final PaymentRepository paymentRepository;
    private final OrderServiceInterface orderService;

    /**
     * Three steps: record the attempt, call the provider, record its answer. Not
     * {@code @Transactional}: the two recording steps are short transactions of
     * their own, and the provider call between them runs with no transaction open,
     * so it holds no pooled connection however long it takes (CLAUDE.md).
     *
     * <p>If the app stops between the steps, or the provider times out, the payment
     * stays {@code PROCESSING}, which blocks another payment and a cancellation of
     * the order until {@link PaymentReconciler} settles it; the order can't end up
     * charged twice, or charged and cancelled.
     */
    @Override
    public PayResult pay(Long orderId, String idempotencyKey, String paymentMethod) {
        PaymentRecorder.Attempt attempt = recorder.start(orderId, idempotencyKey, paymentMethod);
        if (attempt.repeated()) {
            return new PayResult(attempt.payment(), false);
        }
        return new PayResult(chargeAndRecord(attempt.payment()), true);
    }

    private PaymentDto chargeAndRecord(PaymentDto payment) {
        ChargeRequest request = ChargeRequest.forPayment(
                payment.id(), payment.amount(), payment.currency(), payment.paymentMethod());
        ChargeResult result;
        try {
            result = gateway.charge(request);
        } catch (PaymentProviderUnavailableException e) {
            // Still unavailable once the retries ran out. No transaction is open
            // here, so catching it hides nothing from a rollback: the provider never
            // took the charge, and the attempt is recorded as failed.
            log.warn("Payment {}: provider unavailable after retries", payment.id(), e);
            return recorder.recordFailure(payment.id(), PROVIDER_UNAVAILABLE);
        } catch (PaymentOutcomeUnknownException e) {
            // It may have charged, so the attempt isn't failed: it stays PROCESSING
            // (the answer is 202), and PaymentReconciler settles it later from what
            // the provider says happened.
            log.warn("Payment {}: outcome unknown, left for reconciliation", payment.id(), e);
            return payment;
        }
        if (result instanceof ChargeResult.Approved approved) {
            return recorder.recordSuccess(payment.id(), approved);
        }
        return recorder.recordFailure(payment.id(), ((ChargeResult.Declined) result).reason());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentDto> getPayments(Long orderId) {
        requireOrderAccess(orderId);
        return paymentRepository.findAllByOrder(orderId).stream().map(PaymentDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDto getPayment(Long orderId, Long paymentId) {
        requireOrderAccess(orderId);
        return paymentRepository.findByIdAndOrder(paymentId, orderId)
                .map(PaymentDto::from)
                .orElseThrow(() -> new ResourceNotFoundException("payment not found!"));
    }

    /** 404 for an order that doesn't exist, 403 for someone else's (the order module's own check). */
    private void requireOrderAccess(Long orderId) {
        orderService.getOrder(orderId);
    }
}
