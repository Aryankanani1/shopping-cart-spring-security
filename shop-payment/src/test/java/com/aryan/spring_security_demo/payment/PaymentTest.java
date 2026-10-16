package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A payment's lifecycle: started from an order, settled once, and refunded if its order is cancelled. */
class PaymentTest {

    private static final Instant STARTED = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant SETTLED = Instant.parse("2026-10-15T12:00:02Z");

    @Test
    void statusMachine_settlesOnce_thenOnlyASucceededChargeIsRefunded() {
        assertThat(allowedFrom(PaymentStatus.PROCESSING))
                .containsExactlyInAnyOrder(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED);
        assertThat(allowedFrom(PaymentStatus.SUCCEEDED)).containsExactly(PaymentStatus.REFUND_PENDING);
        assertThat(allowedFrom(PaymentStatus.REFUND_PENDING)).containsExactly(PaymentStatus.REFUNDED);
        assertThat(allowedFrom(PaymentStatus.FAILED)).isEmpty();
        assertThat(allowedFrom(PaymentStatus.REFUNDED)).isEmpty();
    }

    @Test
    void refund_isOwed_thenRecorded_andTheChargeItselfNeverChanges() {
        Payment payment = started();
        payment.succeed("ch_1", "Visa", "4242", SETTLED);
        Instant cancelled = Instant.parse("2026-10-16T09:00:00Z");
        Instant refundedAt = Instant.parse("2026-10-16T09:00:01Z");

        payment.requestRefund(cancelled);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(payment.getRefundRequestedAt()).isEqualTo(cancelled);
        assertThat(payment.getRefundReference()).isNull();

        payment.refunded("re_1", refundedAt);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundReference()).isEqualTo("re_1");
        assertThat(payment.getRefundedAt()).isEqualTo(refundedAt);
        // The charge's own facts stay as they were.
        assertThat(payment.getProviderReference()).isEqualTo("ch_1");
        assertThat(payment.getCompletedAt()).isEqualTo(SETTLED);
        assertThat(payment.getAmount()).isEqualByComparingTo("59.97");
    }

    @Test
    void onlyASucceededCharge_canBeRefunded() {
        Payment inProgress = started();
        assertThatThrownBy(() -> inProgress.requestRefund(SETTLED)).isInstanceOf(IllegalStateException.class);

        Payment failed = started();
        failed.fail("Your card was declined.", SETTLED);
        assertThatThrownBy(() -> failed.requestRefund(SETTLED)).isInstanceOf(IllegalStateException.class);

        Payment paid = started();
        paid.succeed("ch_1", "Visa", "4242", SETTLED);
        assertThatThrownBy(() -> paid.refunded("re_1", SETTLED)).isInstanceOf(IllegalStateException.class);
    }

    private static List<PaymentStatus> allowedFrom(PaymentStatus from) {
        return Arrays.stream(PaymentStatus.values()).filter(from::canTransitionTo).toList();
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
