package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A checkout saga's moves, as {@link CheckoutSagaStatus} allows them. */
class CheckoutSagaInstanceTest {

    private static final Instant BOOKED = Instant.parse("2026-11-26T09:00:00Z");
    private static final Instant LATER = BOOKED.plusSeconds(60);

    @Test
    void aCheckoutThatIsPaid_isCompleted() {
        CheckoutSagaInstance saga = CheckoutSagaInstance.started(1L, BOOKED);

        saga.completed(LATER);

        assertThat(saga.getStatus()).isEqualTo(CheckoutSagaStatus.COMPLETED);
        assertThat(saga.getCompensationReason()).isNull();
        assertThat(saga.getCreatedAt()).isEqualTo(BOOKED);
        assertThat(saga.getUpdatedAt()).isEqualTo(LATER);
    }

    // Its payments all failed: compensating until the order is cancelled, keeping why.
    @Test
    void aCheckoutWhoseCancellationIsOwed_isCompensatedOnceTheOrderIsCancelled() {
        CheckoutSagaInstance saga = CheckoutSagaInstance.started(1L, BOOKED);
        saga.compensating(CompensationReason.PAYMENT_ATTEMPTS_USED_UP, BOOKED);

        saga.orderCancelled(false, LATER);

        assertThat(saga.getStatus()).isEqualTo(CheckoutSagaStatus.COMPENSATED);
        assertThat(saga.getCompensationReason()).isEqualTo(CompensationReason.PAYMENT_ATTEMPTS_USED_UP);
    }

    @Test
    void aPaidOrderCancelled_isCompensatingUntilItsRefundIsMade() {
        CheckoutSagaInstance saga = CheckoutSagaInstance.started(1L, BOOKED);
        saga.completed(BOOKED);

        saga.orderCancelled(true, LATER);
        assertThat(saga.getStatus()).isEqualTo(CheckoutSagaStatus.COMPENSATING);
        assertThat(saga.getCompensationReason()).isEqualTo(CompensationReason.ORDER_CANCELLED);

        saga.refunded(LATER);
        assertThat(saga.getStatus()).isEqualTo(CheckoutSagaStatus.COMPENSATED);
    }

    @Test
    void aCompensatedCheckout_movesNoFurther() {
        CheckoutSagaInstance saga = CheckoutSagaInstance.started(1L, BOOKED);
        saga.orderCancelled(false, BOOKED);

        assertThatThrownBy(() -> saga.completed(LATER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> saga.refunded(LATER)).isInstanceOf(IllegalStateException.class);
    }

    // The pivot: once paid, a checkout never waits for a payment again.
    @Test
    void aCompletedCheckout_neverAwaitsPaymentAgain() {
        assertThat(CheckoutSagaStatus.COMPLETED.canTransitionTo(CheckoutSagaStatus.AWAITING_PAYMENT)).isFalse();
        assertThat(CheckoutSagaStatus.COMPENSATING.canTransitionTo(CheckoutSagaStatus.COMPLETED)).isFalse();
    }

    // Its partial refunds had given every cent back already: nothing left to owe.
    @Test
    void aPaidOrderCancelled_withNothingLeftToRefund_isCompensatedAtOnce() {
        CheckoutSagaInstance saga = CheckoutSagaInstance.started(1L, BOOKED);
        saga.completed(BOOKED);

        saga.orderCancelled(false, LATER);

        assertThat(saga.getStatus()).isEqualTo(CheckoutSagaStatus.COMPENSATED);
    }
}
