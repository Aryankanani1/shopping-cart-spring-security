package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Refunding a payment: the provider first, then the record, each only when still owed. */
@ExtendWith(MockitoExtension.class)
class RefundProcessorTest {

    private static final RefundRequest OWED = new RefundRequest("refund_7", "ch_1", new BigDecimal("59.97"), "USD");
    private static final Instant CUTOFF = Instant.parse("2026-10-16T09:00:00Z");

    @Mock private RefundRecorder recorder;
    @Mock private PaymentGatewayClient gateway;

    @InjectMocks private RefundProcessor processor;

    @Test
    void refund_asksTheProvider_thenRecordsTheRefund() {
        when(recorder.pendingRefund(7L)).thenReturn(Optional.of(OWED));
        when(gateway.refund(OWED)).thenReturn(new RefundAttempt.Refunded(new RefundResult("re_1")));

        assertThat(processor.refund(7L)).isTrue();

        InOrder steps = inOrder(gateway, recorder);
        steps.verify(gateway).refund(OWED);
        steps.verify(recorder).recordRefunded(7L, new RefundResult("re_1"));
    }

    @Test
    void refund_alreadyRefunded_doesNotAskTheProviderAgain() {
        when(recorder.pendingRefund(7L)).thenReturn(Optional.empty());

        assertThat(processor.refund(7L)).isTrue();

        verifyNoInteractions(gateway);
    }

    @Test
    void refund_whileTheProviderIsDown_isLeftPendingForTheJob() {
        when(recorder.pendingRefund(7L)).thenReturn(Optional.of(OWED));
        when(gateway.refund(OWED)).thenReturn(new RefundAttempt.StillOwed("down"));

        assertThat(processor.refund(7L)).isFalse();

        verify(recorder, never()).recordRefunded(anyLong(), any());
    }

    @Test
    void retryPendingRefunds_triesEach_andOneFailingDoesNotStopTheOthers() {
        when(recorder.refundsPendingSince(CUTOFF)).thenReturn(List.of(7L, 8L, 9L));
        when(recorder.pendingRefund(7L)).thenThrow(new IllegalStateException("database hiccup"));
        when(recorder.pendingRefund(8L)).thenReturn(Optional.of(OWED));
        when(gateway.refund(OWED)).thenReturn(new RefundAttempt.Refunded(new RefundResult("re_8")));
        when(recorder.pendingRefund(9L)).thenReturn(Optional.empty());

        assertThat(processor.retryPendingRefunds(CUTOFF)).isEqualTo(2);

        verify(recorder).recordRefunded(8L, new RefundResult("re_8"));
    }

    @Test
    void retryPendingRefunds_withTheProviderDown_countsNone() {
        when(recorder.refundsPendingSince(CUTOFF)).thenReturn(List.of(7L));
        when(recorder.pendingRefund(7L)).thenReturn(Optional.of(OWED));
        when(gateway.refund(OWED)).thenReturn(new RefundAttempt.StillOwed("down"));

        assertThat(processor.retryPendingRefunds(CUTOFF)).isZero();
    }
}
