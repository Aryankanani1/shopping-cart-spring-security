package com.aryan.spring_security_demo.exception;

/**
 * Thrown when checkout is attempted with nothing in the cart. Rendered as
 * {@code 409 Conflict}: the request is well-formed, but the cart's current state
 * has nothing to order.
 */
public class EmptyCartException extends RuntimeException {
    public EmptyCartException(String message) {
        super(message);
    }
}
