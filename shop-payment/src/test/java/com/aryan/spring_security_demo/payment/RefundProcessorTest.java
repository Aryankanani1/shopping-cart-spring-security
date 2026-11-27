package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Refunding a payment: the provider first, then the record, each only when still owed. */
@ExtendWith(MockitoExtension.class)
class RefundProcessorTest {

    private static final RefundRequest OWED = new RefundRequest("refund_7", "ch_1", new BigDecimal("59.97"), "USD");

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
    void refund_whileTheProviderIsDown_isLeftPending() {
        when(recorder.pendingRefund(7L)).thenReturn(Optional.of(OWED));
        when(gateway.refund(OWED)).thenReturn(new RefundAttempt.StillOwed("down"));

        assertThat(processor.refund(7L)).isFalse();

        verify(recorder, never()).recordRefunded(anyLong(), any());
    }
}
