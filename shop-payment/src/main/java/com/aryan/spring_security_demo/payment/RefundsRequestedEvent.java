package com.aryan.spring_security_demo.payment;

import java.util.List;

/**
 * Refunds became owed in the transaction that publishes this: they are
 * {@code PENDING} rows. {@link RefundAfterCommitListener} sends them once that
 * transaction commits, and never if it rolls back.
 */
public record RefundsRequestedEvent(List<Long> refundIds) {
}
