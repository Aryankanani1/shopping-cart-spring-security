package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.exception.ConflictException;

/**
 * An order has failed to be paid too many times. The limit stops card testing
 * (trying stolen card numbers against one order until one works).
 */
public class TooManyPaymentAttemptsException extends ConflictException {

    public TooManyPaymentAttemptsException(int limit) {
        super("Too many payment attempts",
                "This order has failed to be paid " + limit + " times; cancel it and place a new order to try again");
    }
}
