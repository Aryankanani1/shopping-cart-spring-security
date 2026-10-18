package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.exception.ConflictException;

/**
 * A refund the payment's state doesn't allow: it isn't paid, its order is
 * cancelled, or there is less left to refund than was asked. A 409.
 */
public class RefundNotPossibleException extends ConflictException {

    public RefundNotPossibleException(String message) {
        super("Refund not possible", message);
    }
}
