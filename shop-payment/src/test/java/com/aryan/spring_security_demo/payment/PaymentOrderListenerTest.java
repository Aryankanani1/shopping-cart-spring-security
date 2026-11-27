package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.OrderCancellingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOrderListenerTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRecorder refundRecorder;
    @InjectMocks private PaymentOrderListener listener;

    @Test
    void anOrderBeingPaidFor_cannotBeCancelled() {
        when(paymentRepository.countByOrdersAndStatus(List.of(1L, 2L), PaymentStatus.PROCESSING)).thenReturn(1L);

        assertThatThrownBy(() -> listener.onOrderCancelling(new OrderCancellingEvent(List.of(1L, 2L))))
                .isInstanceOf(PaymentInProgressException.class);
        verifyNoInteractions(refundRecorder);
    }

    // The refunds (and their outbox messages) are owed in the cancellation's transaction.
    @Test
    void ordersNotBeingPaidFor_canBeCancelled_owingBackWhatWasPaid() {
        when(paymentRepository.countByOrdersAndStatus(List.of(1L, 2L), PaymentStatus.PROCESSING)).thenReturn(0L);

        assertThatCode(() -> listener.onOrderCancelling(new OrderCancellingEvent(List.of(1L, 2L))))
                .doesNotThrowAnyException();

        verify(refundRecorder).requestRefunds(List.of(1L, 2L));
    }
}
