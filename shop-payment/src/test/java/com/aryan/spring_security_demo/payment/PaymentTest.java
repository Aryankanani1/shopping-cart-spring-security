package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A payment's lifecycle: started from an order, settled once, then refunded in part or in full. */
class PaymentTest {

    private static final Instant STARTED = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant SETTLED = Instant.parse("2026-10-15T12:00:02Z");

    private static final Instant LATER = Instant.parse("2026-10-16T09:00:00Z");

    @Test
    void statusMachine_settlesOnce_thenOnlyASucceededChargeIsRefunded() {
        assertThat(allowedFrom(PaymentStatus.PROCESSING))
                .containsExactlyInAnyOrder(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED);
        // REFUNDED straight from SUCCEEDED once partial refunds add up to the payment.
        assertThat(allowedFrom(PaymentStatus.SUCCEEDED))
                .containsExactlyInAnyOrder(PaymentStatus.REFUND_PENDING, PaymentStatus.REFUNDED);
        assertThat(allowedFrom(PaymentStatus.REFUND_PENDING)).containsExactly(PaymentStatus.REFUNDED);
        assertThat(allowedFrom(PaymentStatus.FAILED)).isEmpty();
        assertThat(allowedFrom(PaymentStatus.REFUNDED)).isEmpty();
    }

    @Test
    void partialRefunds_addUp_andThePaymentIsRefunded_onlyOnceEveryCentIsBack() {
        Payment payment = paid();

        Refund goodwill = payment.refund(Refund.Kind.AMOUNT, new BigDecimal("10.00"), "Box damaged", "k1",
                "admin@example.com", LATER);
        assertThat(goodwill.getStatus()).isEqualTo(Refund.Status.PENDING);
        assertThat(goodwill.getIdempotencyKey()).startsWith(Refund.KEY_PREFIX);
        assertThat(payment.refundableAmount()).isEqualByComparingTo("49.97");
        payment.refunded(goodwill, "re_1", LATER);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("10.00");
        assertThat(payment.getStatus()).as("part of it kept").isEqualTo(PaymentStatus.SUCCEEDED);

        Refund rest = payment.refund(Refund.Kind.ITEMS, new BigDecimal("49.97"), "Returned", "k2",
                "admin@example.com", LATER);
        payment.refunded(rest, "re_2", LATER);

        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("59.97");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        // The charge's own facts stay as they were.
        assertThat(payment.getProviderReference()).isEqualTo("ch_1");
        assertThat(payment.getCompletedAt()).isEqualTo(SETTLED);
        assertThat(payment.getAmount()).isEqualByComparingTo("59.97");
    }

    @Test
    void aRefund_isNeverMoreThanIsLeft_countingTheOnesStillOwed() {
        Payment payment = paid();
        payment.refund(Refund.Kind.AMOUNT, new BigDecimal("50.00"), "Goodwill", "k1", "admin@example.com", LATER);

        assertThatThrownBy(() -> payment.refund(Refund.Kind.AMOUNT, new BigDecimal("10.00"), "More", "k2",
                "admin@example.com", LATER))
                .isInstanceOf(RefundNotPossibleException.class)
                .hasMessageContaining("Only 9.97 USD is left");
        assertThatThrownBy(() -> payment.refund(Refund.Kind.AMOUNT, BigDecimal.ZERO, "Nothing", "k3",
                "admin@example.com", LATER)).isInstanceOf(RefundNotPossibleException.class);
    }

    @Test
    void onlyASucceededPayment_ofAnOrderNotCancelled_canBeRefunded() {
        Payment inProgress = started();
        assertThatThrownBy(inProgress::requireRefundable).hasMessageContaining("isn't paid");

        Payment failed = started();
        failed.fail("Your card was declined.", SETTLED);
        assertThatThrownBy(failed::requireRefundable).hasMessageContaining("isn't paid");

        Payment cancelled = paid();
        cancelled.refundTheRest(LATER);
        assertThatThrownBy(() -> cancelled.refund(Refund.Kind.AMOUNT, BigDecimal.ONE, "Late", "k1",
                "admin@example.com", LATER)).hasMessageContaining("cancelled");
    }

    @Test
    void cancelling_refundsWhatIsLeft_afterPartialRefunds() {
        Payment payment = paid();
        Refund partial = payment.refund(Refund.Kind.AMOUNT, new BigDecimal("9.97"), "Goodwill", "k1",
                "admin@example.com", LATER);
        payment.refunded(partial, "re_1", LATER);

        Refund rest = payment.refundTheRest(LATER).orElseThrow();

        assertThat(rest.getKind()).isEqualTo(Refund.Kind.CANCELLATION);
        assertThat(rest.getAmount()).isEqualByComparingTo("50.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        payment.refunded(rest, "re_2", LATER);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void cancelling_whenRefundsOwedAlreadyCoverItAll_owesNothingMore() {
        Payment payment = paid();
        Refund all = payment.refund(Refund.Kind.AMOUNT, new BigDecimal("59.97"), "Everything", "k1",
                "admin@example.com", LATER);

        assertThat(payment.refundTheRest(LATER)).isEmpty();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        payment.refunded(all, "re_1", LATER);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    // The after-commit attempt, the retry job and a webhook can all record one refund.
    @Test
    void recordingARefundAgain_changesNothing() {
        Payment payment = paid();
        Refund refund = payment.refund(Refund.Kind.AMOUNT, new BigDecimal("5.00"), "Goodwill", "k1",
                "admin@example.com", LATER);
        payment.refunded(refund, "re_1", LATER);

        payment.refunded(refund, "re_other", LATER.plusSeconds(60));

        assertThat(refund.getProviderReference()).isEqualTo("re_1");
        assertThat(refund.getRefundedAt()).isEqualTo(LATER);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    void aRepeatedRequest_findsItsRefund_byTheAdminsKey() {
        Payment payment = paid();
        Refund refund = payment.refund(Refund.Kind.AMOUNT, new BigDecimal("5.00"), "Goodwill", "k1",
                "admin@example.com", LATER);

        assertThat(payment.refundRequestedAs("k1")).contains(refund);
        assertThat(payment.refundRequestedAs("k2")).isEmpty();
    }

    @Test
    void chargeKeys_carryThePaymentId_andParseBack() {
        assertThat(Payment.chargeKey(7L)).isEqualTo("pay_7");
        assertThat(Payment.idFromKey("pay_7", Payment.CHARGE_KEY_PREFIX)).contains(7L);
        assertThat(Payment.idFromKey("refund_7", Payment.CHARGE_KEY_PREFIX)).isEmpty();
        assertThat(Payment.idFromKey("pay_x", Payment.CHARGE_KEY_PREFIX)).isEmpty();
        assertThat(Payment.idFromKey(null, Payment.CHARGE_KEY_PREFIX)).isEmpty();
    }

    @Test
    void aWebhookSecret_isEitherOff_orLongEnough() {
        PaymentProperties properties = new PaymentProperties();
        assertThat(properties.isWebhooksEnabled()).isFalse();
        assertThat(properties.isWebhookSecretUsable()).isTrue();

        properties.setWebhookSecret("too-short");
        assertThat(properties.isWebhookSecretUsable()).isFalse();

        properties.setWebhookSecret("x".repeat(32));
        assertThat(properties.isWebhooksEnabled()).isTrue();
        assertThat(properties.isWebhookSecretUsable()).isTrue();
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

    // Stripe's 280-character refusal once failed the save (the column holds 255),
    // which left the payment PROCESSING and answered the customer with a 409.
    @Test
    void fail_shortensAReasonLongerThanItsColumn() {
        Payment payment = started();

        payment.fail("x".repeat(280), SETTLED);

        assertThat(payment.getFailureReason()).hasSize(255).endsWith("…");  // failure_reason varchar(255), V11
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

    private static Payment paid() {
        Payment payment = started();
        payment.succeed("ch_1", "Visa", "4242", SETTLED);
        return payment;
    }
}
