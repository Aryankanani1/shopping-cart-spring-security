package com.aryan.spring_security_demo.exception;

/**
 * Thrown when a caller asks for an order-status change that the lifecycle state
 * machine forbids — e.g. shipping an order that is already delivered, or
 * cancelling one that has already shipped. Rendered as {@code 409 Conflict}: the
 * request is well-formed, but conflicts with the order's current state.
 */
public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
