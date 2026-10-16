package com.aryan.spring_security_demo.payment;

import java.util.List;

public interface PaymentServiceInterface {

    /**
     * Pay for an order with {@code paymentMethod}, charging its total. The same
     * {@code idempotencyKey} for the same order returns the first attempt instead
     * of paying again. A declined payment is a result, not an error: it comes back
     * with status {@code FAILED} and the reason.
     */
    PayResult pay(Long orderId, String idempotencyKey, String paymentMethod);

    /** The order's payment attempts, oldest first. For its owner or an admin. */
    List<PaymentDto> getPayments(Long orderId);

    /** One of the order's payment attempts. For its owner or an admin. */
    PaymentDto getPayment(Long orderId, Long paymentId);

    /** A payment attempt, and whether this request started it (false when its key had already been used). */
    record PayResult(PaymentDto payment, boolean created) {
    }
}
