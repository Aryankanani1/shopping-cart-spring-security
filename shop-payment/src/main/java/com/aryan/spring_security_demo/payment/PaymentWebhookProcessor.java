package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Makes the change a webhook event reports, exactly once. The event's id is
 * recorded and its change made in one transaction: a repeat of the event (the
 * provider delivers at least once) finds the id and does nothing, and two
 * deliveries at the same moment collide on the id's unique key, so only one of
 * them commits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookProcessor {

    /** What processing an event did. */
    public enum Outcome { APPLIED, DUPLICATE, IGNORED }

    private final PaymentWebhookEventRepository events;
    private final PaymentRepository payments;
    private final PaymentRecorder paymentRecorder;
    private final RefundRecorder refundRecorder;
    private final Clock clock;

    @Transactional
    public Outcome process(WebhookEvent event) {
        if (events.existsByEventId(event.id())) {
            return Outcome.DUPLICATE;
        }
        Long paymentId = paymentIdOf(event);
        // Flushed now: a delivery of the same event running at the same moment fails
        // here, on the unique key, before it has changed anything.
        events.saveAndFlush(new PaymentWebhookEvent(event.id(), event.type(), paymentId, Instant.now(clock)));
        if (paymentId == null) {
            // Acknowledged all the same: refusing it would only make the provider retry.
            log.info("Webhook {} ({}) is about nothing this shop handles", event.id(), event.type());
            return Outcome.IGNORED;
        }
        WebhookEvent.Data data = event.data();
        // The same recorder methods as the request, reconciliation and the refund
        // job: whichever gets there first records the outcome, the rest change nothing.
        switch (event.type()) {
            case WebhookEvent.CHARGE_SUCCEEDED -> paymentRecorder.recordSuccess(paymentId,
                    new ChargeResult.Approved(data.providerReference(), data.cardBrand(), data.cardLast4()));
            case WebhookEvent.CHARGE_FAILED -> paymentRecorder.recordFailure(paymentId,
                    data.failureReason() == null ? "The payment provider declined the payment" : data.failureReason());
            case WebhookEvent.REFUND_SUCCEEDED -> refundRecorder.recordRefundedInCurrentTransaction(paymentId,
                    new RefundResult(data.refundReference()));
            default -> throw new IllegalStateException("Unhandled webhook type " + event.type());
        }
        return Outcome.APPLIED;
    }

    /** The payment the event is about, if it is a type this shop handles and the payment exists. */
    private Long paymentIdOf(WebhookEvent event) {
        String prefix = switch (event.type()) {
            case WebhookEvent.CHARGE_SUCCEEDED, WebhookEvent.CHARGE_FAILED -> Payment.CHARGE_KEY_PREFIX;
            case WebhookEvent.REFUND_SUCCEEDED -> Payment.REFUND_KEY_PREFIX;
            default -> null;
        };
        if (prefix == null || event.data() == null) {
            return null;
        }
        return Payment.idFromKey(event.data().idempotencyKey(), prefix).filter(payments::existsById).orElse(null);
    }
}
