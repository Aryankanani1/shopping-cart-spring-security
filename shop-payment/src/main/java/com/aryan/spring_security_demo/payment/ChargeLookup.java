package com.aryan.spring_security_demo.payment;

/**
 * What {@link PaymentGatewayClient#findCharge} found out about a charge whose
 * outcome was lost. "Never received" and "couldn't ask" lead to opposite outcomes
 * (the payment fails, or is asked about again later), so they are told apart by type.
 */
public sealed interface ChargeLookup {

    /** The provider's record of the charge: approved or declined. */
    record Found(ChargeResult result) implements ChargeLookup {
    }

    /** The provider never received the charge: nothing was charged. */
    record NeverReceived() implements ChargeLookup {
    }

    /**
     * The fallback: the provider couldn't be asked (the tries were used up, or its
     * circuit breaker is open), or answered without settling the payment yet. Ask
     * again later.
     */
    record Unanswered(String reason) implements ChargeLookup {
    }
}
