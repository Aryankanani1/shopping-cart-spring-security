package com.aryan.spring_security_demo.order;

import java.util.List;

/**
 * Published just before orders are cancelled, inside the cancelling transaction,
 * so modules that depend on orders can settle what they hold for them first, or
 * refuse: payments records a refund for each paid order, and stops an order being
 * cancelled while it is being paid for. A listener that throws rolls the
 * cancellation back, and what it wrote commits only with the cancellation.
 */
public record OrderCancellingEvent(List<Long> orderIds) {
}
