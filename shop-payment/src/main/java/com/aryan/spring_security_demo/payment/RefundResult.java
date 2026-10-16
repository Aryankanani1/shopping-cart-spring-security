package com.aryan.spring_security_demo.payment;

/** The provider refunded the charge; {@code refundReference} is its id for the refund. */
public record RefundResult(String refundReference) {
}
