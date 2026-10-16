package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Calls the payment provider, retrying while it is unavailable. A bean of its own,
 * called outside any transaction, so a retry repeats only the provider call and
 * never database writes (CLAUDE.md, retries rule). Repeating the call is safe
 * because the idempotency key stays the same: the provider charges once, however
 * many of the calls reach it. Only an unavailable provider is retried, not a timeout:
 * a timed-out charge is left for {@link PaymentReconciler}.
 */
@Component
@RequiredArgsConstructor
public class PaymentGatewayClient {

    private final PaymentGateway gateway;

    /**
     * Charge through the provider. While it is unavailable, tries twice more,
     * after 100 ms and then 200 ms, before letting the exception through.
     */
    @Retryable(includes = PaymentProviderUnavailableException.class, maxRetries = 2, delay = 100, multiplier = 2)
    public ChargeResult charge(ChargeRequest request) {
        return gateway.charge(request);
    }

    /** Ask the provider what it did with a charge, retrying the same way as {@link #charge}. */
    @Retryable(includes = PaymentProviderUnavailableException.class, maxRetries = 2, delay = 100, multiplier = 2)
    public Optional<ChargeResult> findCharge(String idempotencyKey) {
        return gateway.findCharge(idempotencyKey);
    }

    /** Refund through the provider, retrying the same way as {@link #charge}. */
    @Retryable(includes = PaymentProviderUnavailableException.class, maxRetries = 2, delay = 100, multiplier = 2)
    public RefundResult refund(RefundRequest request) {
        return gateway.refund(request);
    }
}
