package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

/** The database steps of a refund, with the repository mocked. */
@ExtendWith(MockitoExtension.class)
class RefundRecorderTest {

    private static final Instant NOW = Instant.parse("2026-10-16T09:00:00Z");

    @Mock private PaymentRepository paymentRepository;
    @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks private RefundRecorder recorder;

    @Test
    void requestRefunds_marksTheOrdersSucceededPaymentsAsOwed() {
        Payment paid = paid();
        when(paymentRepository.findByOrdersAndStatus(List.of(9L), PaymentStatus.SUCCEEDED)).thenReturn(List.of(paid));

        recorder.requestRefunds(List.of(9L));

        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(paid.getRefundRequestedAt()).isEqualTo(NOW);
    }

    @Test
    void pendingRefund_asksForTheWholeCharge_underAKeyOfItsOwn() {
        Payment owed = paid();
        owed.requestRefund(NOW);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(owed));

        assertThat(recorder.pendingRefund(7L)).hasValueSatisfying(request -> {
            assertThat(request.providerReference()).isEqualTo("ch_1");
            assertThat(request.amount()).isEqualByComparingTo("59.97");
            assertThat(request.currency()).isEqualTo("USD");
            // The payment's id is null here (never saved); the key carries whatever it is.
            assertThat(request.idempotencyKey()).startsWith("refund_");
        });
    }

    @Test
    void pendingRefund_ofAPaymentNoLongerOwed_isEmpty() {
        Payment refunded = paid();
        refunded.requestRefund(NOW);
        refunded.refunded("re_1", NOW);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(refunded));

        assertThat(recorder.pendingRefund(7L)).isEmpty();
    }

    @Test
    void recordRefunded_settlesAnOwedRefund() {
        Payment owed = paid();
        owed.requestRefund(NOW);
        when(paymentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(owed));

        recorder.recordRefunded(7L, new RefundResult("re_1"));

        assertThat(owed.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(owed.getRefundReference()).isEqualTo("re_1");
    }

    // The after-commit listener and the retry job can both get here for one refund.
    @Test
    void recordRefunded_twice_keepsTheFirst() {
        Payment refunded = paid();
        refunded.requestRefund(NOW);
        refunded.refunded("re_1", NOW);
        when(paymentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(refunded));

        recorder.recordRefunded(7L, new RefundResult("re_2"));

        assertThat(refunded.getRefundReference()).isEqualTo("re_1");
    }

    // A webhook records its event and the refund in one transaction.
    @Test
    void recordRefundedInCurrentTransaction_settlesAnOwedRefundTheSameWay() {
        Payment owed = paid();
        owed.requestRefund(NOW);
        when(paymentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(owed));

        recorder.recordRefundedInCurrentTransaction(7L, new RefundResult("re_1"));

        assertThat(owed.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundsPendingSince_asksForTheOwedOnesOnly() {
        when(paymentRepository.findIdsByStatusRequestedBefore(PaymentStatus.REFUND_PENDING, NOW))
                .thenReturn(List.of(7L, 8L));

        assertThat(recorder.refundsPendingSince(NOW)).containsExactly(7L, 8L);
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
