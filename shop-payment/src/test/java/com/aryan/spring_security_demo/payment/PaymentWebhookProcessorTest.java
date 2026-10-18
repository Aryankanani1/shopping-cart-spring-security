package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Each event's change, made once, through the same recorders as everywhere else. */
@ExtendWith(MockitoExtension.class)
class PaymentWebhookProcessorTest {

    @Mock private PaymentWebhookEventRepository events;
    @Mock private PaymentRepository payments;
    @Mock private RefundRepository refunds;
    @Mock private PaymentRecorder paymentRecorder;
    @Mock private RefundRecorder refundRecorder;
    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks private PaymentWebhookProcessor processor;

    @Test
    void chargeSucceeded_recordsTheEvent_thenTheSuccess() {
        when(payments.existsById(7L)).thenReturn(true);

        assertThat(processor.process(event(WebhookEvent.CHARGE_SUCCEEDED, "pay_7"))).isEqualTo(PaymentWebhookProcessor.Outcome.APPLIED);

        InOrder steps = inOrder(events, paymentRecorder);
        steps.verify(events).saveAndFlush(argThat(saved -> saved.getEventId().equals("evt_1") && saved.getPaymentId() == 7L));
        steps.verify(paymentRecorder).recordSuccess(7L, new ChargeResult.Approved("ch_1", "Visa", "4242"));
    }

    @Test
    void chargeFailed_recordsTheFailureWithItsReason() {
        when(payments.existsById(7L)).thenReturn(true);

        processor.process(event(WebhookEvent.CHARGE_FAILED, "pay_7"));

        verify(paymentRecorder).recordFailure(7L, "Your card was declined.");
    }

    @Test
    void refundSucceeded_recordsTheRefund_foundByItsKey_inThisTransaction() {
        // refund_7: a key V17 kept, from before partial refunds; new ones start rfd_.
        when(refunds.findRefundOf("refund_7")).thenReturn(Optional.of(new RefundRepository.RefundOf(11L, 7L)));

        processor.process(event(WebhookEvent.REFUND_SUCCEEDED, "refund_7"));

        verify(refundRecorder).recordRefundedInCurrentTransaction(11L, new RefundResult("re_1"));
        verify(events).saveAndFlush(argThat(recorded -> Long.valueOf(7L).equals(recorded.getPaymentId())));
    }

    @Test
    void aRefundEventForARefundTheShopDoesntKnow_isIgnored() {
        when(refunds.findRefundOf("rfd_unknown")).thenReturn(Optional.empty());

        assertThat(processor.process(event(WebhookEvent.REFUND_SUCCEEDED, "rfd_unknown")))
                .isEqualTo(PaymentWebhookProcessor.Outcome.IGNORED);
        verifyNoInteractions(refundRecorder);
    }

    @Test
    void aRepeatedEvent_changesNothing() {
        when(events.existsByEventId("evt_1")).thenReturn(true);

        assertThat(processor.process(event(WebhookEvent.CHARGE_SUCCEEDED, "pay_7")))
                .isEqualTo(PaymentWebhookProcessor.Outcome.DUPLICATE);

        verify(events, never()).saveAndFlush(any());
        verifyNoInteractions(paymentRecorder);
    }

    // Recorded and acknowledged: refusing it would only make the provider retry.
    @Test
    void anUnknownTypeOrPayment_isRecordedAndIgnored() {
        assertThat(processor.process(event("customer.updated", "pay_7")))
                .isEqualTo(PaymentWebhookProcessor.Outcome.IGNORED);
        when(payments.existsById(8L)).thenReturn(false);
        assertThat(processor.process(event(WebhookEvent.CHARGE_SUCCEEDED, "pay_8")))
                .isEqualTo(PaymentWebhookProcessor.Outcome.IGNORED);
        // A charge event naming a refund key isn't about any charge.
        assertThat(processor.process(event(WebhookEvent.CHARGE_SUCCEEDED, "refund_7")))
                .isEqualTo(PaymentWebhookProcessor.Outcome.IGNORED);

        verifyNoInteractions(paymentRecorder, refundRecorder);
        verify(events, times(3)).saveAndFlush(argThat(saved -> saved.getPaymentId() == null));
    }

    /** A refund owed from a payment, with the ids saving would give them. */
    private static WebhookEvent event(String type, String key) {
        return new WebhookEvent("evt_1", type, 1_760_616_000L, new WebhookEvent.Data(
                key, "ch_1", "Visa", "4242", "Your card was declined.", "re_1"));
    }
}
