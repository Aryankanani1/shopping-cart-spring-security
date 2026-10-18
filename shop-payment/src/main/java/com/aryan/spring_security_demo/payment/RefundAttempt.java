package com.aryan.spring_security_demo.payment;

/** What became of a request to refund a payment, from {@link PaymentGatewayClient#refund}. */
public sealed interface RefundAttempt {

    /** The provider refunded it. */
    record Refunded(RefundResult refund) implements RefundAttempt {
    }

    /**
     * The fallback: the provider couldn't be reached (the tries were used up, or its
     * circuit breaker is open). Nothing was refunded; the refund is still owed, and
     * the retry job tries again.
     */
    record StillOwed(String reason) implements RefundAttempt {
    }
}
