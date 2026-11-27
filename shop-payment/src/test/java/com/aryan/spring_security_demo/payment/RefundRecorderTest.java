package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The database steps of a refund, with the repositories mocked. */
@ExtendWith(MockitoExtension.class)
class RefundRecorderTest {

    private static final Instant NOW = Instant.parse("2026-10-16T09:00:00Z");
    private static final Long PAYMENT_ID = 7L;
    private static final Long REFUND_ID = 11L;

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private CheckoutSaga checkoutSaga;
    @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks private RefundRecorder recorder;

    @Test
    void pendingRefund_asksForTheRefundsAmount_underItsOwnKey() {
        Refund refund = owed(paid(), "10.00");
        when(refundRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));

        assertThat(recorder.pendingRefund(REFUND_ID)).hasValueSatisfying(request -> {
            assertThat(request.providerReference()).isEqualTo("ch_1");
            assertThat(request.amount()).isEqualByComparingTo("10.00");
            assertThat(request.currency()).isEqualTo("USD");
            assertThat(request.idempotencyKey()).isEqualTo(refund.getIdempotencyKey()).startsWith("rfd_");
        });
    }

    @Test
    void pendingRefund_ofARefundMadeAlready_isEmpty() {
        Payment paid = paid();
        Refund refund = owed(paid, "10.00");
        paid.refunded(refund, "re_1", NOW);
        when(refundRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));

        assertThat(recorder.pendingRefund(REFUND_ID)).isEmpty();
    }

    @Test
    void recordRefunded_settlesTheRefund_underThePaymentsLock() {
        Payment paid = paid();
        Refund refund = owed(paid, "59.97");
        lookedUp(paid, refund);

        recorder.recordRefunded(REFUND_ID, new RefundResult("re_1"));

        assertThat(refund.getStatus()).isEqualTo(Refund.Status.REFUNDED);
        assertThat(refund.getProviderReference()).isEqualTo("re_1");
        assertThat(paid.getStatus()).as("every cent back").isEqualTo(PaymentStatus.REFUNDED);
        // If the order was cancelled, that was the checkout's last compensation.
        verify(checkoutSaga).paymentRefunded(9L);
    }

    // The outbox can deliver one refund's message twice, and a webhook can get here too.
    @Test
    void recordRefunded_twice_keepsTheFirst() {
        Payment paid = paid();
        Refund refund = owed(paid, "10.00");
        lookedUp(paid, refund);
        recorder.recordRefunded(REFUND_ID, new RefundResult("re_1"));

        recorder.recordRefunded(REFUND_ID, new RefundResult("re_2"));

        assertThat(refund.getProviderReference()).isEqualTo("re_1");
        assertThat(paid.getRefundedAmount()).as("counted once").isEqualByComparingTo("10.00");
        assertThat(paid.getStatus()).as("part of it kept").isEqualTo(PaymentStatus.SUCCEEDED);
        verifyNoInteractions(checkoutSaga);
    }

    // A webhook records its event and the refund in one transaction.
    @Test
    void recordRefundedInCurrentTransaction_settlesTheRefundTheSameWay() {
        Payment paid = paid();
        Refund refund = owed(paid, "10.00");
        lookedUp(paid, refund);

        recorder.recordRefundedInCurrentTransaction(REFUND_ID, new RefundResult("re_1"));

        assertThat(refund.getStatus()).isEqualTo(Refund.Status.REFUNDED);
    }

    private void lookedUp(Payment paid, Refund refund) {
        when(refundRepository.findPaymentIdById(REFUND_ID)).thenReturn(Optional.of(PAYMENT_ID));
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(paid));
        when(refundRepository.findByIdForUpdate(REFUND_ID)).thenReturn(Optional.of(refund));
    }

    /** A refund owed from {@code payment}, with the id saving would give it. */
    private static Refund owed(Payment payment, String amount) {
        Refund refund = payment.refund(Refund.Kind.AMOUNT, new BigDecimal(amount), "Goodwill", "k1",
                "admin@example.com", NOW);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }

    private static Payment paid() {
        Order order = new Order();
        order.setId(9L);
        order.setTotalAmount(new BigDecimal("59.97"));
        Payment payment = Payment.start(order, "key-12345", FakePaymentGateway.VISA, "USD", NOW);
        payment.succeed("ch_1", "Visa", "4242", NOW);
        return payment;
    }
}
