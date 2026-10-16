package com.aryan.spring_security_demo.payment;

import java.util.List;

/**
 * Refunds became owed in the transaction that publishes this: their payments are
 * {@code REFUND_PENDING}. {@link RefundAfterCommitListener} starts them once that
 * transaction commits, and never if it rolls back.
 */
public record RefundsRequestedEvent(List<Long> paymentIds) {
}
