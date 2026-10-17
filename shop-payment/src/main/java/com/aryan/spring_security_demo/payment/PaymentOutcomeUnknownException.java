package com.aryan.spring_security_demo.payment;

/**
 * The charge was sent, but no answer came back in time: the provider may or may
 * not have taken the money. So the attempt is neither retried in the request (each
 * try could take the whole timeout) nor marked failed (it may have charged). It
 * stays {@code PROCESSING}, and {@link PaymentReconciler} settles it later from
 * what the provider says happened.
 */
public class PaymentOutcomeUnknownException extends RuntimeException {

    public PaymentOutcomeUnknownException(String message) {
        super(message);
    }

    public PaymentOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
