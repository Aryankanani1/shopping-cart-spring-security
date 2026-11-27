package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.outbox.OutboxHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sends owed refunds to the provider: the outbox's {@code payment.refund}
 * messages, one per {@link Refund}, written in the transaction that owed it (a
 * cancellation's, or an admin's partial refund). A refund the provider can't take
 * yet stays owed, and its message is delivered again later.
 */
@Component
@RequiredArgsConstructor
public class RefundOutboxHandler implements OutboxHandler {

    static final String TYPE = "payment.refund";

    private final RefundProcessor refundProcessor;

    @Override
    public String type() {
        return TYPE;
    }

    /** {@code aggregateId} is the refund's id. */
    @Override
    public boolean handle(Long aggregateId) {
        return refundProcessor.refund(aggregateId);
    }
}
