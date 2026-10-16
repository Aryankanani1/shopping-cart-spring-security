package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A payment's lifecycle: started from an order, then settled once. */
class PaymentTest {

    private static final Instant STARTED = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant SETTLED = Instant.parse("2026-10-15T12:00:02Z");

    @Test
    void statusMachine_onlyAnAttemptInProgressCanBeSettled() {
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.SUCCEEDED)).isTrue();
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
        for (PaymentStatus target : PaymentStatus.values()) {
            assertThat(PaymentStatus.SUCCEEDED.canTransitionTo(target)).as("SUCCEEDED -> %s", target).isFalse();
            assertThat(PaymentStatus.FAILED.canTransitionTo(target)).as("FAILED -> %s", target).isFalse();
        }
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.PROCESSING)).isFalse();
    }

    @Test
    void start_chargesTheOrdersTotal_andIsInProgress() {
        Payment payment = started();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.getAmount()).isEqualByComparingTo("59.97");
        assertThat(payment.getCurrency()).isEqualTo("USD");
        assertThat(payment.getIdempotencyKey()).isEqualTo("key-12345");
        assertThat(payment.getPaymentMethod()).isEqualTo(FakePaymentGateway.VISA);
        assertThat(payment.getCreatedAt()).isEqualTo(STARTED);
        assertThat(payment.getCompletedAt()).isNull();
    }

    @Test
    void succeed_recordsTheChargeAndWhen() {
        Payment payment = started();

        payment.succeed("ch_1", "Visa", "4242", SETTLED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderReference()).isEqualTo("ch_1");
        assertThat(payment.getCardBrand()).isEqualTo("Visa");
        assertThat(payment.getCardLast4()).isEqualTo("4242");
        assertThat(payment.getCompletedAt()).isEqualTo(SETTLED);
    }

    @Test
    void fail_recordsTheReasonAndWhen() {
        Payment payment = started();

        payment.fail("Your card was declined.", SETTLED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Your card was declined.");
        assertThat(payment.getCompletedAt()).isEqualTo(SETTLED);
    }

    @Test
    void aSettledOutcome_neverChanges() {
        Payment payment = started();
        payment.fail("Your card was declined.", SETTLED);

        assertThatThrownBy(() -> payment.succeed("ch_1", "Visa", "4242", SETTLED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.fail("again", SETTLED))
                .isInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Your card was declined.");
    }

    private static Payment started() {
        Order order = new Order();
        order.setId(9L);
        order.setTotalAmount(new BigDecimal("59.97"));
        return Payment.start(order, "key-12345", FakePaymentGateway.VISA, "USD", STARTED);
    }
}
