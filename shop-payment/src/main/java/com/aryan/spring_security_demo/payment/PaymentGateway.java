package com.aryan.spring_security_demo.payment;

import java.time.Duration;
import java.util.Optional;

/**
 * The boundary with the payment provider: {@link FakePaymentGateway}, simulated in
 * the app, or {@link StripePaymentGateway}, Stripe's API in test mode
 * ({@code app.payment.provider}).
 *
 * <p>Never called inside a database transaction (CLAUDE.md): a call can take
 * seconds, and a rollback can't undo a charge.
 */
public interface PaymentGateway {

    /**
     * Ask the provider to charge {@code request}. Charging the same
     * {@link ChargeRequest#idempotencyKey()} again must not charge twice: the
     * provider answers with the first result.
     *
     * @return whether the provider approved or declined the charge
     * @throws PaymentProviderUnavailableException if the provider couldn't be
     *         reached; the charge didn't happen and may be retried with the same key
     * @throws PaymentOutcomeUnknownException if the request was sent but no answer
     *         came back; the provider may have charged
     */
    ChargeResult charge(ChargeRequest request);

    /**
     * What the provider did with {@code request}, a charge sent before: its answer,
     * or empty if it never received one. How a payment whose outcome was lost gets
     * settled. It takes the whole request, the same as was sent, because a provider
     * may look a charge up by sending it again (Stripe replays a request only if its
     * parameters match); doing so must never charge.
     *
     * @throws PaymentProviderUnavailableException if the provider couldn't be reached
     */
    Optional<ChargeResult> findCharge(ChargeRequest request);

    /**
     * Ask the provider to refund a charge in full. Refunding the same
     * {@link RefundRequest#idempotencyKey()} again must not refund twice: the
     * provider answers with the first refund.
     *
     * @throws PaymentProviderUnavailableException if the provider couldn't be
     *         reached; nothing was refunded, and it may be retried with the same key
     */
    RefundResult refund(RefundRequest request);

    /**
     * Empty while the provider may be called. While it is known to be failing (its
     * circuit breaker is open), at most how long until it is tried again: a payment
     * is then refused before an attempt is recorded.
     */
    default Optional<Duration> unavailableFor() {
        return Optional.empty();
    }
}
