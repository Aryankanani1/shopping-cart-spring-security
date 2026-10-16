package com.aryan.spring_security_demo.payment;

import java.util.Optional;

/**
 * The boundary with the payment provider. {@link FakePaymentGateway} is the only
 * implementation: this project simulates its money. A real provider would be
 * another implementation calling its HTTPS API, with its key read from the
 * environment.
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
     * What the provider did with the charge sent under {@code idempotencyKey}: its
     * answer, or empty if it never received one. How a payment whose outcome was
     * lost gets settled.
     *
     * @throws PaymentProviderUnavailableException if the provider couldn't be reached
     */
    Optional<ChargeResult> findCharge(String idempotencyKey);

    /**
     * Ask the provider to refund a charge in full. Refunding the same
     * {@link RefundRequest#idempotencyKey()} again must not refund twice: the
     * provider answers with the first refund.
     *
     * @throws PaymentProviderUnavailableException if the provider couldn't be
     *         reached; nothing was refunded, and it may be retried with the same key
     */
    RefundResult refund(RefundRequest request);
}
