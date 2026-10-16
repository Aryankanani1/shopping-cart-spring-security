package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.InvalidOrderStateException;
import com.aryan.spring_security_demo.order.Order;
import com.aryan.spring_security_demo.order.OrderServiceInterface;
import com.aryan.spring_security_demo.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The database steps of a payment, with the order module and repository mocked:
 * which attempts may start, what a repeated key gets, and what each outcome
 * changes. The locking and the races are in PaymentIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class PaymentRecorderTest {

    private static final Long ORDER_ID = 9L;
    private static final Long PAYMENT_ID = 7L;
    private static final String KEY = "key-12345";
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderServiceInterface orderService;
    @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks private PaymentRecorder recorder;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(ORDER_ID);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("59.97"));
    }

    @Test
    void start_recordsAnAttemptInProgress_forTheOrdersTotal() {
        when(orderService.lockForPayment(ORDER_ID)).thenReturn(order);
        when(paymentRepository.findByOrderAndKey(ORDER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentRecorder.Attempt attempt = recorder.start(ORDER_ID, KEY, FakePaymentGateway.VISA);

        assertThat(attempt.repeated()).isFalse();
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("59.97");
        assertThat(saved.getValue().getCurrency()).isEqualTo(PaymentRecorder.CURRENCY);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(attempt.payment().orderId()).isEqualTo(ORDER_ID);
        verify(orderService).startPayment(order);
    }

    @Test
    void start_locksTheOrder_beforeLookingForTheKey() {
        when(orderService.lockForPayment(ORDER_ID)).thenReturn(order);
        when(paymentRepository.findByOrderAndKey(ORDER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        recorder.start(ORDER_ID, KEY, FakePaymentGateway.VISA);

        InOrder steps = inOrder(orderService, paymentRepository);
        steps.verify(orderService).lockForPayment(ORDER_ID);
        steps.verify(paymentRepository).findByOrderAndKey(ORDER_ID, KEY);
    }

    // The first request was paid and moved the order to PAID; its repeat must get
    // that answer, not "this order can't be paid".
    @Test
    void start_sameKeyAgain_returnsTheFirstAttempt_evenOnceTheOrderIsPaid() {
        Payment first = Payment.start(order, KEY, FakePaymentGateway.VISA, "USD", NOW);
        first.succeed("ch_1", "Visa", "4242", NOW);
        order.setOrderStatus(OrderStatus.PAID);
        when(orderService.lockForPayment(ORDER_ID)).thenReturn(order);
        when(paymentRepository.findByOrderAndKey(ORDER_ID, KEY)).thenReturn(Optional.of(first));

        PaymentRecorder.Attempt attempt = recorder.start(ORDER_ID, KEY, FakePaymentGateway.VISA);

        assertThat(attempt.repeated()).isTrue();
        assertThat(attempt.payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository, never()).save(any());
        // A repeat commits, so raising the version would break the first request's
        // recording of its outcome (a version conflict) while it is in flight.
        verify(orderService, never()).startPayment(any());
    }

    @Test
    void start_sameKeyWithAnotherMethod_isRefused() {
        Payment first = Payment.start(order, KEY, FakePaymentGateway.VISA, "USD", NOW);
        when(orderService.lockForPayment(ORDER_ID)).thenReturn(order);
        when(paymentRepository.findByOrderAndKey(ORDER_ID, KEY)).thenReturn(Optional.of(first));

        assertThatThrownBy(() -> recorder.start(ORDER_ID, KEY, FakePaymentGateway.MASTERCARD))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void start_forAnOrderThatIsNotPending_isRefused() {
        order.setOrderStatus(OrderStatus.CANCELLED);
        when(orderService.lockForPayment(ORDER_ID)).thenReturn(order);
        when(paymentRepository.findByOrderAndKey(ORDER_ID, KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recorder.start(ORDER_ID, KEY, FakePaymentGateway.VISA))
                .isInstanceOf(InvalidOrderStateException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void start_whileAnotherPaymentIsInProgress_isRefused() {
        when(orderService.lockForPayment(ORDER_ID)).thenReturn(order);
        when(paymentRepository.findByOrderAndKey(ORDER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentRepository.countByOrderAndStatus(ORDER_ID, PaymentStatus.PROCESSING)).thenReturn(1L);

        assertThatThrownBy(() -> recorder.start(ORDER_ID, KEY, FakePaymentGateway.VISA))
                .isInstanceOf(PaymentInProgressException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void start_afterTheLastAllowedFailure_isRefused() {
        when(orderService.lockForPayment(ORDER_ID)).thenReturn(order);
        when(paymentRepository.findByOrderAndKey(ORDER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentRepository.countByOrderAndStatus(ORDER_ID, PaymentStatus.PROCESSING)).thenReturn(0L);
        when(paymentRepository.countByOrderAndStatus(ORDER_ID, PaymentStatus.FAILED))
                .thenReturn((long) PaymentRecorder.MAX_FAILED_ATTEMPTS);

        assertThatThrownBy(() -> recorder.start(ORDER_ID, KEY, FakePaymentGateway.VISA))
                .isInstanceOf(TooManyPaymentAttemptsException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void start_withFailuresBelowTheLimit_isAllowed() {
        when(orderService.lockForPayment(ORDER_ID)).thenReturn(order);
        when(paymentRepository.findByOrderAndKey(ORDER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentRepository.countByOrderAndStatus(ORDER_ID, PaymentStatus.PROCESSING)).thenReturn(0L);
        when(paymentRepository.countByOrderAndStatus(ORDER_ID, PaymentStatus.FAILED))
                .thenReturn((long) PaymentRecorder.MAX_FAILED_ATTEMPTS - 1);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(recorder.start(ORDER_ID, KEY, FakePaymentGateway.VISA).repeated()).isFalse();
    }

    @Test
    void recordSuccess_settlesThePayment_andMarksTheOrderPaid() {
        Payment payment = Payment.start(order, KEY, FakePaymentGateway.VISA, "USD", NOW);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        PaymentDto result = recorder.recordSuccess(PAYMENT_ID, new ChargeResult.Approved("ch_1", "Visa", "4242"));

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(result.providerReference()).isEqualTo("ch_1");
        assertThat(result.completedAt()).isEqualTo(NOW);
        verify(orderService).markPaid(ORDER_ID);
    }

    @Test
    void recordFailure_settlesThePayment_andLeavesTheOrderAlone() {
        Payment payment = Payment.start(order, KEY, FakePaymentGateway.DECLINED, "USD", NOW);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        PaymentDto result = recorder.recordFailure(PAYMENT_ID, "Your card was declined.");

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("Your card was declined.");
        verify(orderService, never()).markPaid(any());
    }

    // A late request and the reconciliation job can both settle one attempt: the
    // first records it, the second changes nothing.
    @Test
    void recordSuccess_ofAnAttemptAlreadySettled_changesNothing() {
        Payment payment = Payment.start(order, KEY, FakePaymentGateway.VISA, "USD", NOW);
        payment.succeed("ch_1", "Visa", "4242", NOW);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        PaymentDto result = recorder.recordSuccess(PAYMENT_ID, new ChargeResult.Approved("ch_2", "Visa", "4242"));

        assertThat(result.providerReference()).isEqualTo("ch_1");
        verify(orderService, never()).markPaid(any());
    }

    @Test
    void recordFailure_ofAnAttemptThatSucceeded_keepsTheSuccess() {
        Payment payment = Payment.start(order, KEY, FakePaymentGateway.VISA, "USD", NOW);
        payment.succeed("ch_1", "Visa", "4242", NOW);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThat(recorder.recordFailure(PAYMENT_ID, "declined").status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void isProcessing_untilTheAttemptIsSettled() {
        Payment payment = Payment.start(order, KEY, FakePaymentGateway.VISA, "USD", NOW);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThat(recorder.isProcessing(PAYMENT_ID)).isTrue();
        payment.fail("declined", NOW);
        assertThat(recorder.isProcessing(PAYMENT_ID)).isFalse();
    }

    @Test
    void stuckPayments_areTheOnesStillProcessing() {
        when(paymentRepository.findIdsByStatusCreatedBefore(PaymentStatus.PROCESSING, NOW)).thenReturn(List.of(7L));

        assertThat(recorder.stuckPayments(NOW)).containsExactly(7L);
    }

    @Test
    void recordingAMissingPayment_isABug() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recorder.recordFailure(PAYMENT_ID, "declined"))
                .isInstanceOf(IllegalStateException.class);
    }
}
