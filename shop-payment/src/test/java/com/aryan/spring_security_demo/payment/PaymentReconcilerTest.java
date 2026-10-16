package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Settling a payment whose outcome was lost, from the provider's record of its charge. */
@ExtendWith(MockitoExtension.class)
class PaymentReconcilerTest {

    private static final Instant CUTOFF = Instant.parse("2026-10-16T09:00:00Z");

    @Mock private PaymentRecorder recorder;
    @Mock private PaymentGatewayClient gateway;

    @InjectMocks private PaymentReconciler reconciler;

    @Test
    void theProviderCharged_soThePaymentSucceeds() {
        ChargeResult.Approved approved = new ChargeResult.Approved("ch_1", "Visa", "4242");
        when(recorder.isProcessing(7L)).thenReturn(true);
        when(gateway.findCharge("pay_7")).thenReturn(Optional.of(approved));

        assertThat(reconciler.reconcile(7L)).isTrue();

        verify(recorder).recordSuccess(7L, approved);
    }

    @Test
    void theProviderDeclined_soThePaymentFailsWithItsReason() {
        when(recorder.isProcessing(7L)).thenReturn(true);
        when(gateway.findCharge("pay_7")).thenReturn(Optional.of(new ChargeResult.Declined("Your card was declined.")));

        reconciler.reconcile(7L);

        verify(recorder).recordFailure(7L, "Your card was declined.");
    }

    @Test
    void theProviderNeverReceivedIt_soNothingWasCharged() {
        when(recorder.isProcessing(7L)).thenReturn(true);
        when(gateway.findCharge("pay_7")).thenReturn(Optional.empty());

        reconciler.reconcile(7L);

        verify(recorder).recordFailure(7L, PaymentReconciler.NEVER_RECEIVED);
    }

    @Test
    void anAttemptSettledSince_isNotLookedUp() {
        when(recorder.isProcessing(7L)).thenReturn(false);

        assertThat(reconciler.reconcile(7L)).isTrue();

        verifyNoInteractions(gateway);
    }

    @Test
    void whileTheProviderIsDown_thePaymentStaysProcessing() {
        when(recorder.isProcessing(7L)).thenReturn(true);
        when(gateway.findCharge("pay_7")).thenThrow(new PaymentProviderUnavailableException("down"));

        assertThat(reconciler.reconcile(7L)).isFalse();

        verify(recorder, never()).recordFailure(anyLong(), anyString());
        verify(recorder, never()).recordSuccess(anyLong(), any());
    }

    @Test
    void reconcileStuckPayments_settlesEach_andOneFailingDoesNotStopTheOthers() {
        when(recorder.stuckPayments(CUTOFF)).thenReturn(List.of(7L, 8L));
        when(recorder.isProcessing(7L)).thenThrow(new IllegalStateException("database hiccup"));
        when(recorder.isProcessing(8L)).thenReturn(true);
        when(gateway.findCharge("pay_8")).thenReturn(Optional.empty());

        assertThat(reconciler.reconcileStuckPayments(CUTOFF)).isEqualTo(1);

        verify(recorder).recordFailure(8L, PaymentReconciler.NEVER_RECEIVED);
    }
}
