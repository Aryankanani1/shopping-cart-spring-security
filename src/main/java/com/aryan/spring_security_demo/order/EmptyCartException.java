package com.aryan.spring_security_demo.order;

import com.aryan.spring_security_demo.common.exception.ConflictException;

/**
 * Thrown when checkout is attempted with nothing in the cart. Rendered as
 * {@code 409 Conflict}: the request is well-formed, but the cart's current state
 * has nothing to order.
 */
public class EmptyCartException extends ConflictException {
    public EmptyCartException(String message) {
        super("Cart is empty", message);
    }
}
