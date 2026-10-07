package com.aryan.spring_security_demo.order;

import com.aryan.spring_security_demo.common.exception.ConflictException;

/**
 * Thrown when a caller asks for an order-status change that the lifecycle state
 * machine forbids — e.g. shipping an order that is already delivered, or
 * cancelling one that has already shipped. Rendered as {@code 409 Conflict}: the
 * request is well-formed, but conflicts with the order's current state.
 */
public class InvalidOrderStateException extends ConflictException {
    public InvalidOrderStateException(String message) {
        super("Invalid order state", message);
    }
}
