package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

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
    private final RefundRepository refunds;
    private final PaymentRecorder paymentRecorder;
    private final RefundRecorder refundRecorder;
    private final Clock clock;

    @Transactional
    public Outcome process(WebhookEvent event) {
        if (events.existsByEventId(event.id())) {
            return Outcome.DUPLICATE;
        }
        Optional<RefundRepository.RefundOf> refund = refundOf(event);
        Long paymentId = refund.map(RefundRepository.RefundOf::paymentId).orElseGet(() -> chargedPaymentIdOf(event));
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
            case WebhookEvent.REFUND_SUCCEEDED -> refundRecorder.recordRefundedInCurrentTransaction(
                    refund.orElseThrow().refundId(), new RefundResult(data.refundReference()));
            default -> throw new IllegalStateException("Unhandled webhook type " + event.type());
        }
        return Outcome.APPLIED;
    }

    /** The refund a refund event is about, found by the shop's key with the provider. */
    private Optional<RefundRepository.RefundOf> refundOf(WebhookEvent event) {
        if (!WebhookEvent.REFUND_SUCCEEDED.equals(event.type()) || event.data() == null
                || event.data().idempotencyKey() == null) {
            return Optional.empty();
        }
        return refunds.findRefundOf(event.data().idempotencyKey());
    }

    /** The payment a charge event is about, if the payment exists. */
    private Long chargedPaymentIdOf(WebhookEvent event) {
        boolean charge = WebhookEvent.CHARGE_SUCCEEDED.equals(event.type())
                || WebhookEvent.CHARGE_FAILED.equals(event.type());
        if (!charge || event.data() == null) {
            return null;
        }
        return Payment.idFromKey(event.data().idempotencyKey(), Payment.CHARGE_KEY_PREFIX)
                .filter(payments::existsById).orElse(null);
    }
}
