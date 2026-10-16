package com.aryan.spring_security_demo.payment;

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
     */
    ChargeResult charge(ChargeRequest request);
}
