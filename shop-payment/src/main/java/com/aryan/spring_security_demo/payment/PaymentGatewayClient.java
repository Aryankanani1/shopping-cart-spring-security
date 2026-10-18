package com.aryan.spring_security_demo.payment;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Calls the payment provider with Resilience4j's Retry, and when the tries are used
 * up, or the provider's circuit breaker is open, runs the call's fallback instead
 * of throwing (docs/payment-design.md, section 10). A bean of its own, called
 * outside any transaction, so a retry repeats only the provider call and never
 * database writes (CLAUDE.md, retries rule). Repeating the call is safe because the
 * idempotency key stays the same: the provider charges once, however many of the
 * calls reach it.
 *
 * <p>What is retried is in {@code application.yml} ({@code resilience4j.retry}):
 * three tries, 100 ms and 200 ms apart, while the provider is unavailable; not a
 * timeout, since the provider may have charged ({@link PaymentReconciler} finds out),
 * nor a call the open breaker refused, nor a lookup the provider answered without
 * settling.
 */
@Component
@Slf4j
public class PaymentGatewayClient {

    /** The retries' names; their settings are under {@code resilience4j.retry.instances}. */
    static final String CHARGE_RETRY = "payment-charge";
    static final String LOOKUP_RETRY = "payment-lookup";
    static final String REFUND_RETRY = "payment-refund";

    static final String PROVIDER_UNAVAILABLE = "The payment provider is unavailable; please try again in a moment";

    private final PaymentGateway gateway;
    private final Retry chargeRetry;
    private final Retry lookupRetry;
    private final Retry refundRetry;

    @Autowired
    public PaymentGatewayClient(PaymentGateway gateway, RetryRegistry retries) {
        this(gateway, retries.retry(CHARGE_RETRY), retries.retry(LOOKUP_RETRY), retries.retry(REFUND_RETRY));
    }

    /** Takes the retries, so that tests can set them up without Spring. */
    PaymentGatewayClient(PaymentGateway gateway, Retry chargeRetry, Retry lookupRetry, Retry refundRetry) {
        this.gateway = gateway;
        this.chargeRetry = chargeRetry;
        this.lookupRetry = lookupRetry;
        this.refundRetry = refundRetry;
    }

    /**
     * Charge through the provider: approved, declined, or {@link ChargeResult.Unavailable}
     * from the fallback.
     *
     * @throws PaymentOutcomeUnknownException if the charge was sent but no answer
     *         came back: it may have charged, so it isn't a fallback's to decide
     */
    public ChargeResult charge(ChargeRequest request) {
        try {
            return chargeRetry.executeSupplier(() -> gateway.charge(request));
        } catch (PaymentProviderUnavailableException e) {
            return chargeFallback(request, e);
        }
    }

    /** Ask the provider what it did with a charge whose outcome was lost. */
    public ChargeLookup findCharge(ChargeRequest request) {
        Optional<ChargeResult> answer;
        try {
            answer = lookupRetry.executeSupplier(() -> gateway.findCharge(request));
        } catch (PaymentProviderUnavailableException e) {
            return lookupFallback(request, e);
        }
        return answer.<ChargeLookup>map(ChargeLookup.Found::new).orElseGet(ChargeLookup.NeverReceived::new);
    }

    /** Refund through the provider: refunded, or {@link RefundAttempt.StillOwed} from the fallback. */
    public RefundAttempt refund(RefundRequest request) {
        try {
            return new RefundAttempt.Refunded(refundRetry.executeSupplier(() -> gateway.refund(request)));
        } catch (PaymentProviderUnavailableException e) {
            return refundFallback(request, e);
        }
    }

    /** Empty while the provider may be called; see {@link PaymentGateway#unavailableFor()}. */
    public Optional<Duration> unavailableFor() {
        return gateway.unavailableFor();
    }

    // --- the fallbacks: what happens when the provider can't be reached -----

    /** Nothing was charged: the payment is recorded as failed, and the customer may try again. */
    private ChargeResult chargeFallback(ChargeRequest request, PaymentProviderUnavailableException e) {
        log.warn("Charge {}: provider unavailable after the retries, falling back to failing it", request.idempotencyKey(), e);
        return new ChargeResult.Unavailable(PROVIDER_UNAVAILABLE);
    }

    /** The payment stays as it is, for the next reconciliation run to ask again. */
    private ChargeLookup lookupFallback(ChargeRequest request, PaymentProviderUnavailableException e) {
        log.warn("Lookup of charge {}: no answer ({}), falling back to asking on the next run",
                request.idempotencyKey(), e.getMessage());
        return new ChargeLookup.Unanswered(e.getMessage());
    }

    /** Nothing was refunded: the refund stays owed, for the retry job. */
    private RefundAttempt refundFallback(RefundRequest request, PaymentProviderUnavailableException e) {
        log.warn("Refund {}: provider unavailable after the retries, falling back to the retry job",
                request.idempotencyKey(), e);
        return new RefundAttempt.StillOwed(e.getMessage());
    }
}
