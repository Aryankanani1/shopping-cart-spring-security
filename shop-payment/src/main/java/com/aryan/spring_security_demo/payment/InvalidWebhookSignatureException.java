package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.exception.AuthenticationFailedException;

/** A webhook request that isn't provably from the provider: unsigned, badly signed, or too old (401). */
public class InvalidWebhookSignatureException extends AuthenticationFailedException {

    public InvalidWebhookSignatureException(String message) {
        // The detail stays generic: it tells a caller nothing about why it failed.
        super("The webhook signature could not be verified", message);
    }
}
