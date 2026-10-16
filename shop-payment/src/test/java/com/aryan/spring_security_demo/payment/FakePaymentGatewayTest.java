package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The simulated provider answers by payment method, and once per idempotency key. */
class FakePaymentGatewayTest {

    private final FakePaymentGateway gateway = new FakePaymentGateway();

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

        assertThat(gateway.findCharge("pay_1")).hasValueSatisfying(answer ->
                assertThat(answer).isInstanceOf(ChargeResult.Approved.class));
    }

    @Test
    void timeoutNotCharged_neverReachesTheProvider() {
        assertThatThrownBy(() -> gateway.charge(request("pay_1", FakePaymentGateway.TIMEOUT_NOT_CHARGED)))
                .isInstanceOf(PaymentOutcomeUnknownException.class);

        assertThat(gateway.findCharge("pay_1")).isEmpty();
    }

    @Test
    void findCharge_reportsEachKeysAnswer() {
        ChargeResult declined = gateway.charge(request("pay_1", FakePaymentGateway.DECLINED));

        assertThat(gateway.findCharge("pay_1")).contains(declined);
        assertThat(gateway.findCharge("pay_2")).isEmpty();
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

    private static RefundRequest refund(String key, String providerReference) {
        return new RefundRequest(key, providerReference, new BigDecimal("10.00"), "USD");
    }

    private static ChargeRequest request(String key, String method) {
        return new ChargeRequest(key, new BigDecimal("10.00"), "USD", method);
    }
}
