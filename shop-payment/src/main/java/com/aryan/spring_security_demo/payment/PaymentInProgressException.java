package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.exception.ConflictException;

/** An order already has a payment the provider hasn't answered yet. */
public class PaymentInProgressException extends ConflictException {

    public PaymentInProgressException(String message) {
        super("Payment in progress", message);
    }
}
