package com.aryan.spring_security_demo.payment;

/**
 * What became of a charge: approved or declined by the provider, or, from
 * {@link PaymentGatewayClient}'s fallback, not tried at all because the provider
 * couldn't be reached.
 */
public sealed interface ChargeResult {

    /** The money was taken. {@code providerReference} is the provider's id for the charge. */
    record Approved(String providerReference, String cardBrand, String cardLast4) implements ChargeResult {
    }

    /** Nothing was taken; {@code reason} can be shown to the customer. */
    record Declined(String reason) implements ChargeResult {
    }

    /**
     * Not the provider's answer: the fallback once the tries were used up, or while
     * its circuit breaker is open. Nothing was charged, and {@code reason} can be
     * shown to the customer.
     */
    record Unavailable(String reason) implements ChargeResult {
    }
}
