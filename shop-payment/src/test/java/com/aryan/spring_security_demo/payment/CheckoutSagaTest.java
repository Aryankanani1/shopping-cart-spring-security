package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.outbox.Outbox;
import com.aryan.spring_security_demo.order.Order;
import com.aryan.spring_security_demo.order.OrderServiceInterface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The checkout saga's decisions, with the repositories, the order module and the outbox mocked. */
@ExtendWith(MockitoExtension.class)
class CheckoutSagaTest {

    private static final Instant NOW = Instant.parse("2026-11-26T09:00:00Z");
    private static final Long ORDER_ID = 9L;
    private static final Long OTHER_ORDER_ID = 10L;
    private static final Long REFUND_ID = 11L;

    @Mock private CheckoutSagaInstanceRepository sagas;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private OrderServiceInterface orderService;
    @Mock private Outbox outbox;
    @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks private CheckoutSaga checkoutSaga;

    @Test
    void anOrderBooked_startsItsSaga_awaitingPayment() {
        checkoutSaga.orderBooked(ORDER_ID);

        ArgumentCaptor<CheckoutSagaInstance> saved = ArgumentCaptor.forClass(CheckoutSagaInstance.class);
        verify(sagas).save(saved.capture());
        assertThat(saved.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(saved.getValue().getStatus()).isEqualTo(CheckoutSagaStatus.AWAITING_PAYMENT);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void aPaymentSucceeding_completesTheSaga() {
        CheckoutSagaInstance saga = awaitingPayment(ORDER_ID);

        checkoutSaga.paymentSucceeded(ORDER_ID);

        assertThat(saga.getStatus()).isEqualTo(CheckoutSagaStatus.COMPLETED);
    }

    @Test
    void aFailedPayment_withAttemptsLeft_changesNothing() {
        when(paymentRepository.countByOrderAndStatus(ORDER_ID, PaymentStatus.FAILED))
                .thenReturn((long) PaymentRecorder.MAX_FAILED_ATTEMPTS - 1);

        checkoutSaga.paymentFailed(ORDER_ID);

        verifyNoInteractions(sagas, outbox);
    }

    // The order is cancelled once this commits, in a transaction of its own.
    @Test
    void theLastFailedPayment_owesTheOrdersCancellation() {
        when(paymentRepository.countByOrderAndStatus(ORDER_ID, PaymentStatus.FAILED))
                .thenReturn((long) PaymentRecorder.MAX_FAILED_ATTEMPTS);
        CheckoutSagaInstance saga = awaitingPayment(ORDER_ID);

        checkoutSaga.paymentFailed(ORDER_ID);

        assertThat(saga.getStatus()).isEqualTo(CheckoutSagaStatus.COMPENSATING);
        assertThat(saga.getCompensationReason()).isEqualTo(CompensationReason.PAYMENT_ATTEMPTS_USED_UP);
        verify(outbox).append(CheckoutCompensationHandler.TYPE, ORDER_ID);
    }

    // The same failure reported again (a webhook after the request) owes nothing more.
    @Test
    void aFailureAfterTheCompensationIsOwed_owesNoSecondOne() {
        when(paymentRepository.countByOrderAndStatus(ORDER_ID, PaymentStatus.FAILED))
                .thenReturn((long) PaymentRecorder.MAX_FAILED_ATTEMPTS);
        CheckoutSagaInstance saga = CheckoutSagaInstance.started(ORDER_ID, NOW);
        saga.compensating(CompensationReason.PAYMENT_ATTEMPTS_USED_UP, NOW);
        when(sagas.findByOrderIdsForUpdate(List.of(ORDER_ID))).thenReturn(List.of(saga));

        checkoutSaga.paymentFailed(ORDER_ID);

        verifyNoInteractions(outbox);
    }

    @Test
    void compensating_cancelsTheUnpaidOrder() {
        when(orderService.cancelUnpaid(ORDER_ID)).thenReturn(true);

        checkoutSaga.compensate(ORDER_ID);

        verify(orderService).cancelUnpaid(ORDER_ID);
    }

    // Cancelled by its owner before the message was delivered: done all the same.
    @Test
    void compensating_anOrderCancelledMeanwhile_isDone() {
        when(orderService.cancelUnpaid(ORDER_ID)).thenReturn(false);

        checkoutSaga.compensate(ORDER_ID);

        verifyNoInteractions(sagas);
    }

    @Test
    void theDeadline_cancelsTheUnpaidOrder_andSaysWhy() {
        when(orderService.expireIfUnpaid(ORDER_ID, NOW)).thenReturn(true);
        CheckoutSagaInstance saga = CheckoutSagaInstance.started(ORDER_ID, NOW);
        saga.orderCancelled(false, NOW);  // what the cancellation did, through orderCancelling
        when(sagas.findByOrderIdsForUpdate(List.of(ORDER_ID))).thenReturn(List.of(saga));

        assertThat(checkoutSaga.expire(ORDER_ID, NOW)).isTrue();

        assertThat(saga.getStatus()).isEqualTo(CheckoutSagaStatus.COMPENSATED);
        assertThat(saga.getCompensationReason()).isEqualTo(CompensationReason.PAYMENT_DEADLINE_PASSED);
    }

    @Test
    void theDeadline_ofAnOrderPaidMeanwhile_changesNothing() {
        when(orderService.expireIfUnpaid(ORDER_ID, NOW)).thenReturn(false);

        assertThat(checkoutSaga.expire(ORDER_ID, NOW)).isFalse();

        verifyNoInteractions(sagas);
    }

    // Step 2's outcome isn't known yet: the provider may still take the money.
    @Test
    void anOrderBeingPaidFor_cannotBeCancelled() {
        when(paymentRepository.countByOrdersAndStatus(List.of(ORDER_ID), PaymentStatus.PROCESSING)).thenReturn(1L);

        assertThatThrownBy(() -> checkoutSaga.orderCancelling(List.of(ORDER_ID)))
                .isInstanceOf(PaymentInProgressException.class);

        verify(paymentRepository, never()).findByOrdersAndStatusForUpdate(any(), any());
        verifyNoInteractions(sagas, outbox);
    }

    @Test
    void cancellingOrders_refundsThePaidOne_andCompensatesBoth() {
        List<Long> orderIds = List.of(ORDER_ID, OTHER_ORDER_ID);
        when(paymentRepository.countByOrdersAndStatus(orderIds, PaymentStatus.PROCESSING)).thenReturn(0L);
        Payment paid = paid();
        when(paymentRepository.findByOrdersAndStatusForUpdate(orderIds, PaymentStatus.SUCCEEDED)).thenReturn(List.of(paid));
        when(refundRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Refund> owed = invocation.getArgument(0);
            owed.forEach(refund -> ReflectionTestUtils.setField(refund, "id", REFUND_ID));
            return owed;
        });
        when(paymentRepository.findOrderIdsByStatus(orderIds, PaymentStatus.REFUND_PENDING)).thenReturn(List.of(ORDER_ID));
        CheckoutSagaInstance paidSaga = CheckoutSagaInstance.started(ORDER_ID, NOW);
        paidSaga.completed(NOW);
        CheckoutSagaInstance unpaidSaga = CheckoutSagaInstance.started(OTHER_ORDER_ID, NOW);
        when(sagas.findByOrderIdsForUpdate(orderIds)).thenReturn(List.of(paidSaga, unpaidSaga));

        checkoutSaga.orderCancelling(orderIds);

        // Step 2 compensated: the rest of the payment is owed back, and its message has it sent.
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(paid.getRefunds()).singleElement().satisfies(refund -> {
            assertThat(refund.getKind()).isEqualTo(Refund.Kind.CANCELLATION);
            assertThat(refund.getAmount()).isEqualByComparingTo("59.97");
        });
        verify(outbox).append(RefundOutboxHandler.TYPE, REFUND_ID);
        // Compensating until the refund is made; the unpaid one is compensated already.
        assertThat(paidSaga.getStatus()).isEqualTo(CheckoutSagaStatus.COMPENSATING);
        assertThat(paidSaga.getCompensationReason()).isEqualTo(CompensationReason.ORDER_CANCELLED);
        assertThat(unpaidSaga.getStatus()).isEqualTo(CheckoutSagaStatus.COMPENSATED);
    }

    @Test
    void theRefund_ofACancelledOrder_completesItsCompensation() {
        CheckoutSagaInstance saga = CheckoutSagaInstance.started(ORDER_ID, NOW);
        saga.completed(NOW);
        saga.orderCancelled(true, NOW);
        when(sagas.findByOrderIdsForUpdate(List.of(ORDER_ID))).thenReturn(List.of(saga));

        checkoutSaga.paymentRefunded(ORDER_ID);

        assertThat(saga.getStatus()).isEqualTo(CheckoutSagaStatus.COMPENSATED);
    }

    // Partial refunds can give every cent back while the order stays delivered.
    @Test
    void aPaymentRefundedInFull_ofAnOrderNotCancelled_leavesItsCheckoutCompleted() {
        CheckoutSagaInstance saga = CheckoutSagaInstance.started(ORDER_ID, NOW);
        saga.completed(NOW);
        when(sagas.findByOrderIdsForUpdate(List.of(ORDER_ID))).thenReturn(List.of(saga));

        checkoutSaga.paymentRefunded(ORDER_ID);

        assertThat(saga.getStatus()).isEqualTo(CheckoutSagaStatus.COMPLETED);
    }

    private CheckoutSagaInstance awaitingPayment(Long orderId) {
        CheckoutSagaInstance saga = CheckoutSagaInstance.started(orderId, NOW);
        when(sagas.findByOrderIdsForUpdate(List.of(orderId))).thenReturn(List.of(saga));
        return saga;
    }

    private static Payment paid() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setTotalAmount(new BigDecimal("59.97"));
        Payment payment = Payment.start(order, "key-12345", FakePaymentGateway.VISA, "USD", NOW);
        payment.succeed("ch_1", "Visa", "4242", NOW);
        return payment;
    }
}
