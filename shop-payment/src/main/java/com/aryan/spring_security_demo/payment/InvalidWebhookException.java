package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.exception.BadRequestException;

/** A correctly signed webhook whose body isn't an event this shop can read (400). */
public class InvalidWebhookException extends BadRequestException {

    public InvalidWebhookException(String message) {
        super("Invalid webhook", message);
    }
}
