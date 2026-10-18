package com.aryan.spring_security_demo.payment;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The retries and fallbacks of {@link PaymentGatewayClient} (docs/payment-design.md,
 * section 10), with Resilience4j's real Retry and application.yml's rules (the
 * context test checks they match), waiting 1 ms between tries instead of 100.
 */
class PaymentGatewayClientTest {

    private static final ChargeRequest CHARGE =
            new ChargeRequest("pay_7", new BigDecimal("25.00"), "USD", FakePaymentGateway.VISA);
    private static final RefundRequest REFUND =
            new RefundRequest("refund_7", "pi_1", new BigDecimal("25.00"), "USD");
    private static final ChargeResult.Approved APPROVED = new ChargeResult.Approved("pi_1", "Visa", "4242");

    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final PaymentGatewayClient client =
            new PaymentGatewayClient(gateway, retry("charge"), retry("lookup"), retry("refund"));

    // --- charge -----------------------------------------------------------

    @Test
    void aProviderThatStaysUnavailable_isTriedThreeTimes_thenTheFallbackFailsThePayment() {
        when(gateway.charge(CHARGE)).thenThrow(new PaymentProviderUnavailableException("down"));

        assertThat(client.charge(CHARGE))
                .isEqualTo(new ChargeResult.Unavailable(PaymentGatewayClient.PROVIDER_UNAVAILABLE));
        verify(gateway, times(3)).charge(CHARGE);
    }

    @Test
    void aProviderBackOnTheSecondTry_answers_withoutTheFallback() {
        when(gateway.charge(CHARGE)).thenThrow(new PaymentProviderUnavailableException("blip")).thenReturn(APPROVED);

        assertThat(client.charge(CHARGE)).isEqualTo(APPROVED);
        verify(gateway, times(2)).charge(CHARGE);
    }

    @Test
    void anOpenBreaker_goesStraightToTheFallback() {
        when(gateway.charge(CHARGE)).thenThrow(new PaymentProviderCircuitOpenException("open", null));

        assertThat(client.charge(CHARGE)).isInstanceOf(ChargeResult.Unavailable.class);
        verify(gateway, times(1)).charge(CHARGE);
    }

    // It may have charged: that is reconciliation's to find out, not a fallback's to decide.
    @Test
    void anUnknownOutcome_isNeitherRetriedNorAFallback() {
        when(gateway.charge(CHARGE)).thenThrow(new PaymentOutcomeUnknownException("timeout"));

        assertThatThrownBy(() -> client.charge(CHARGE)).isInstanceOf(PaymentOutcomeUnknownException.class);
        verify(gateway, times(1)).charge(CHARGE);
    }

    @Test
    void aDecline_isTheAnswer_triedOnce() {
        ChargeResult.Declined declined = new ChargeResult.Declined("Your card was declined.");
        when(gateway.charge(CHARGE)).thenReturn(declined);

        assertThat(client.charge(CHARGE)).isEqualTo(declined);
        verify(gateway, times(1)).charge(CHARGE);
    }

    // --- findCharge ---------------------------------------------------------

    @Test
    void aLookup_reportsTheCharge_orThatItNeverArrived() {
        when(gateway.findCharge(CHARGE)).thenReturn(Optional.of(APPROVED)).thenReturn(Optional.empty());

        assertThat(client.findCharge(CHARGE)).isEqualTo(new ChargeLookup.Found(APPROVED));
        assertThat(client.findCharge(CHARGE)).isEqualTo(new ChargeLookup.NeverReceived());
    }

    @Test
    void aLookupThatCantReachTheProvider_isTriedThreeTimes_thenUnanswered() {
        when(gateway.findCharge(CHARGE)).thenThrow(new PaymentProviderUnavailableException("down"));

        assertThat(client.findCharge(CHARGE)).isInstanceOf(ChargeLookup.Unanswered.class);
        verify(gateway, times(3)).findCharge(CHARGE);
    }

    @Test
    void aLookupAnsweredWithoutSettling_isUnanswered_triedOnce() {
        when(gateway.findCharge(CHARGE)).thenThrow(new PaymentNotSettledException("still processing"));

        assertThat(client.findCharge(CHARGE)).isEqualTo(new ChargeLookup.Unanswered("still processing"));
        verify(gateway, times(1)).findCharge(CHARGE);
    }

    // --- refund -------------------------------------------------------------

    @Test
    void aRefund_isRefunded() {
        when(gateway.refund(REFUND)).thenReturn(new RefundResult("re_1"));

        assertThat(client.refund(REFUND)).isEqualTo(new RefundAttempt.Refunded(new RefundResult("re_1")));
    }

    @Test
    void aRefundThatCantReachTheProvider_isTriedThreeTimes_thenStillOwed() {
        when(gateway.refund(REFUND)).thenThrow(new PaymentProviderUnavailableException("down"));

        assertThat(client.refund(REFUND)).isInstanceOf(RefundAttempt.StillOwed.class);
        verify(gateway, times(3)).refund(REFUND);
    }

    @Test
    void aRefundTheProviderRefused_isNeitherRetriedNorAFallback() {
        when(gateway.refund(REFUND)).thenThrow(new IllegalStateException("Stripe refused refund"));

        assertThatThrownBy(() -> client.refund(REFUND)).isInstanceOf(IllegalStateException.class);
        verify(gateway, times(1)).refund(REFUND);
    }

    /** application.yml's payment-provider rules, with a 1 ms wait. */
    private static Retry retry(String name) {
        return Retry.of(name, RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(PaymentProviderUnavailableException.class)
                .ignoreExceptions(PaymentProviderCircuitOpenException.class, PaymentNotSettledException.class)
                .build());
    }
}
