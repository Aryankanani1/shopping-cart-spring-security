package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.exception.ConflictException;

/**
 * An {@code Idempotency-Key} came back with a different request. A key names one
 * attempt, so answering with that attempt's result would answer a question the
 * client didn't ask.
 */
public class IdempotencyKeyReusedException extends ConflictException {

    public IdempotencyKeyReusedException() {
        super("Idempotency key reused",
                "This Idempotency-Key was already used for a payment with a different payment method");
    }
}
