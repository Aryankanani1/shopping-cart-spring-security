package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** The simulated provider answers by payment method, and once per idempotency key. */
class FakePaymentGatewayTest {

    private final FakeWebhookSender webhooks = mock(FakeWebhookSender.class);
    private final PaymentProperties properties = new PaymentProperties();
    private final List<Duration> waits = new ArrayList<>();
    private final FakePaymentGateway gateway = new FakePaymentGateway(webhooks, properties, waits::add);

    @Test
    void testCards_areApproved() {
        assertThat(gateway.charge(request("pay_1", FakePaymentGateway.VISA)))
                .isInstanceOfSatisfying(ChargeResult.Approved.class, approved -> {
                    assertThat(approved.cardBrand()).isEqualTo("Visa");
                    assertThat(approved.cardLast4()).isEqualTo("4242");
                    assertThat(approved.providerReference()).startsWith("ch_");
                });
        assertThat(gateway.charge(request("pay_2", FakePaymentGateway.MASTERCARD)))
                .isInstanceOfSatisfying(ChargeResult.Approved.class,
                        approved -> assertThat(approved.cardLast4()).isEqualTo("4444"));
    }

    @Test
    void decliningMethods_andUnknownOnes_areDeclinedWithAReason() {
        assertThat(gateway.charge(request("pay_1", FakePaymentGateway.DECLINED)))
                .isEqualTo(new ChargeResult.Declined("Your card was declined."));
        assertThat(gateway.charge(request("pay_2", FakePaymentGateway.INSUFFICIENT_FUNDS)))
                .isEqualTo(new ChargeResult.Declined("Your card has insufficient funds."));
        assertThat(gateway.charge(request("pay_3", "4242424242424242")))
                .isEqualTo(new ChargeResult.Declined("Unknown payment method."));
    }

    @Test
    void theSameKey_getsTheFirstAnswer_withoutANewCharge() {
        ChargeResult first = gateway.charge(request("pay_1", FakePaymentGateway.VISA));

        assertThat(gateway.charge(request("pay_1", FakePaymentGateway.VISA))).isEqualTo(first);
        assertThat(gateway.charge(request("pay_2", FakePaymentGateway.VISA))).isNotEqualTo(first);
    }

    @Test
    void flaky_isUnavailableOnTheFirstTryOfEachCharge_thenApproved() {
        assertThatThrownBy(() -> gateway.charge(request("pay_1", FakePaymentGateway.FLAKY)))
                .isInstanceOf(PaymentProviderUnavailableException.class);
        assertThat(gateway.charge(request("pay_1", FakePaymentGateway.FLAKY)))
                .isInstanceOf(ChargeResult.Approved.class);
        assertThatThrownBy(() -> gateway.charge(request("pay_2", FakePaymentGateway.FLAKY)))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void providerDown_isUnavailableEveryTime() {
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> gateway.charge(request("pay_1", FakePaymentGateway.PROVIDER_DOWN)))
                    .isInstanceOf(PaymentProviderUnavailableException.class);
        }
    }

    @Test
    void timeoutCharged_losesTheAnswer_butTheProviderKnowsItCharged() {
        assertThatThrownBy(() -> gateway.charge(request("pay_1", FakePaymentGateway.TIMEOUT_CHARGED)))
                .isInstanceOf(PaymentOutcomeUnknownException.class);

        assertThat(gateway.findCharge(request("pay_1", FakePaymentGateway.TIMEOUT_CHARGED))).hasValueSatisfying(answer ->
                assertThat(answer).isInstanceOf(ChargeResult.Approved.class));
    }

    @Test
    void timeoutNotCharged_neverReachesTheProvider() {
        assertThatThrownBy(() -> gateway.charge(request("pay_1", FakePaymentGateway.TIMEOUT_NOT_CHARGED)))
                .isInstanceOf(PaymentOutcomeUnknownException.class);

        assertThat(gateway.findCharge(request("pay_1", FakePaymentGateway.TIMEOUT_NOT_CHARGED))).isEmpty();
    }

    @Test
    void findCharge_reportsEachKeysAnswer() {
        ChargeResult declined = gateway.charge(request("pay_1", FakePaymentGateway.DECLINED));

        assertThat(gateway.findCharge(request("pay_1", FakePaymentGateway.DECLINED))).contains(declined);
        assertThat(gateway.findCharge(request("pay_2", FakePaymentGateway.VISA))).isEmpty();
    }

    @Test
    void refund_isGivenOnce_perKey() {
        ChargeResult.Approved charge = (ChargeResult.Approved) gateway.charge(request("pay_1", FakePaymentGateway.VISA));

        RefundResult first = gateway.refund(refund("refund_1", charge.providerReference()));

        assertThat(first.refundReference()).startsWith("re_");
        assertThat(gateway.refund(refund("refund_1", charge.providerReference()))).isEqualTo(first);
    }

    @Test
    void refundDown_failsTheFirstTryAndItsRetries_thenRefunds() {
        ChargeResult.Approved charge =
                (ChargeResult.Approved) gateway.charge(request("pay_1", FakePaymentGateway.REFUND_DOWN));

        for (int call = 0; call < FakePaymentGateway.REFUND_DOWN_CALLS; call++) {
            assertThatThrownBy(() -> gateway.refund(refund("refund_1", charge.providerReference())))
                    .isInstanceOf(PaymentProviderUnavailableException.class);
        }
        assertThat(gateway.refund(refund("refund_1", charge.providerReference())).refundReference())
                .startsWith("re_");
    }

    // After a restart the fake has forgotten its charges; a refund still goes through.
    @Test
    void refund_ofAChargeItNoLongerKnows_isStillGiven() {
        assertThat(gateway.refund(refund("refund_9", "ch_forgotten")).refundReference()).startsWith("re_");
    }

    // ---- webhooks: each new charge or refund is reported once ------------

    @Test
    void anApprovedCharge_isReportedOnce_evenWhenRepeated() {
        ChargeResult.Approved charge = (ChargeResult.Approved) gateway.charge(request("pay_1", FakePaymentGateway.VISA));
        gateway.charge(request("pay_1", FakePaymentGateway.VISA));

        verify(webhooks, times(1)).send(eq(WebhookEvent.CHARGE_SUCCEEDED), argThat(data ->
                data.idempotencyKey().equals("pay_1") && data.providerReference().equals(charge.providerReference())
                        && data.cardLast4().equals("4242")));
    }

    @Test
    void aDeclinedCharge_isReportedWithItsReason() {
        gateway.charge(request("pay_1", FakePaymentGateway.DECLINED));

        verify(webhooks).send(eq(WebhookEvent.CHARGE_FAILED),
                argThat(data -> data.failureReason().equals("Your card was declined.")));
    }

    // The case webhooks exist for: the answer was lost, the webhook tells the shop.
    @Test
    void aChargeWhoseAnswerTimedOut_isStillReported() {
        assertThatThrownBy(() -> gateway.charge(request("pay_1", FakePaymentGateway.TIMEOUT_CHARGED)))
                .isInstanceOf(PaymentOutcomeUnknownException.class);

        verify(webhooks).send(eq(WebhookEvent.CHARGE_SUCCEEDED), argThat(data -> data.idempotencyKey().equals("pay_1")));
    }

    @Test
    void nothingIsReported_forAChargeThatNeverReachedTheProvider() {
        assertThatThrownBy(() -> gateway.charge(request("pay_1", FakePaymentGateway.TIMEOUT_NOT_CHARGED)))
                .isInstanceOf(PaymentOutcomeUnknownException.class);
        assertThatThrownBy(() -> gateway.charge(request("pay_2", FakePaymentGateway.PROVIDER_DOWN)))
                .isInstanceOf(PaymentProviderUnavailableException.class);

        verify(webhooks, never()).send(anyString(), any());
    }

    @Test
    void aRefund_isReportedOnce() {
        ChargeResult.Approved charge = (ChargeResult.Approved) gateway.charge(request("pay_1", FakePaymentGateway.VISA));
        RefundResult refund = gateway.refund(refund("refund_1", charge.providerReference()));
        gateway.refund(refund("refund_1", charge.providerReference()));

        verify(webhooks, times(1)).send(eq(WebhookEvent.REFUND_SUCCEEDED), argThat(data ->
                data.idempotencyKey().equals("refund_1") && data.refundReference().equals(refund.refundReference())));
    }

    // A simulated round trip on every call; the default (zero) answers at once.
    @Test
    void aConfiguredLatency_delaysEveryProviderCall() {
        gateway.charge(request("pay_1", FakePaymentGateway.VISA));
        assertThat(waits).isEmpty();

        properties.getFake().setLatency(Duration.ofMillis(300));
        ChargeResult.Approved charge = (ChargeResult.Approved) gateway.charge(request("pay_2", FakePaymentGateway.VISA));
        gateway.findCharge(request("pay_2", FakePaymentGateway.VISA));
        gateway.refund(refund("refund_2", charge.providerReference()));

        assertThat(waits).containsExactly(Duration.ofMillis(300), Duration.ofMillis(300), Duration.ofMillis(300));
    }

    private static RefundRequest refund(String key, String providerReference) {
        return new RefundRequest(key, providerReference, new BigDecimal("10.00"), "USD");
    }

    private static ChargeRequest request(String key, String method) {
        return new ChargeRequest(key, new BigDecimal("10.00"), "USD", method);
    }
}
