package com.aryan.spring_security_demo.order;

/**
 * Published when an order is placed, inside the checkout's transaction, once the
 * order is saved and its stock reserved: payments starts the order's checkout saga
 * (docs/payment-design.md, section 13), which commits only with the order.
 */
public record OrderPlacedEvent(Long orderId) {
}
