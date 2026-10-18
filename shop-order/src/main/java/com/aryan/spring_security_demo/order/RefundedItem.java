package com.aryan.spring_security_demo.order;

import java.math.BigDecimal;

/**
 * An order item refunded in part or in full, and what it cost the customer: the
 * quantity times the unit price paid ({@link OrderServiceInterface#refundItems}).
 */
public record RefundedItem(Long orderItemId, int quantity, BigDecimal amount) {
}
