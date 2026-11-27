package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.outbox.OutboxHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Compensates step 1 of a checkout whose payments all failed: the outbox's
 * {@code checkout.compensate} messages, written by {@link CheckoutSaga#paymentFailed}
 * in the transaction that recorded the last failure. A cancellation that fails (it
 * raced another change to the order) is delivered again later.
 */
@Component
@RequiredArgsConstructor
public class CheckoutCompensationHandler implements OutboxHandler {

    static final String TYPE = "checkout.compensate";

    private final CheckoutSaga checkoutSaga;

    @Override
    public String type() {
        return TYPE;
    }

    /** {@code aggregateId} is the order's id. */
    @Override
    public boolean handle(Long aggregateId) {
        checkoutSaga.compensate(aggregateId);
        return true;
    }
}
