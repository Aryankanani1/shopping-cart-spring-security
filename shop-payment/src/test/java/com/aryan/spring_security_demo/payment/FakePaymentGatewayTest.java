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

    private static ChargeRequest request(String key, String method) {
        return new ChargeRequest(key, new BigDecimal("10.00"), "USD", method);
    }
}
