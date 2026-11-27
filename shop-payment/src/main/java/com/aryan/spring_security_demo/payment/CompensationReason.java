package com.aryan.spring_security_demo.payment;

/** Why an order's checkout saga was compensated. */
public enum CompensationReason {

    /** Every payment attempt allowed failed ({@link PaymentRecorder#MAX_FAILED_ATTEMPTS}). */
    PAYMENT_ATTEMPTS_USED_UP,

    /** The order was left unpaid past its deadline ({@code app.orders.unpaid-expiry}). */
    PAYMENT_DEADLINE_PASSED,

    /** The customer or an admin cancelled the order, or the customer deleted their account. */
    ORDER_CANCELLED
}
