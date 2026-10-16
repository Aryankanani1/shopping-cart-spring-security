package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.OrderCancellingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOrderListenerTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRecorder refundRecorder;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private PaymentOrderListener listener;

    @Test
    void anOrderBeingPaidFor_cannotBeCancelled() {
        when(paymentRepository.countByOrdersAndStatus(List.of(1L, 2L), PaymentStatus.PROCESSING)).thenReturn(1L);

        assertThatThrownBy(() -> listener.onOrderCancelling(new OrderCancellingEvent(List.of(1L, 2L))))
                .isInstanceOf(PaymentInProgressException.class);
        verifyNoInteractions(refundRecorder, events);
    }

    @Test
    void anUnpaidOrder_canBeCancelled_withNothingToRefund() {
        when(paymentRepository.countByOrdersAndStatus(List.of(1L), PaymentStatus.PROCESSING)).thenReturn(0L);
        when(refundRecorder.requestRefunds(List.of(1L))).thenReturn(List.of());

        assertThatCode(() -> listener.onOrderCancelling(new OrderCancellingEvent(List.of(1L))))
                .doesNotThrowAnyException();
        verify(events, never()).publishEvent(any(Object.class));
    }

    // The refund is owed in the cancellation's transaction, and started once it commits.
    @Test
    void paidOrders_oweARefund_startedAfterTheCommit() {
        when(paymentRepository.countByOrdersAndStatus(List.of(1L, 2L), PaymentStatus.PROCESSING)).thenReturn(0L);
        when(refundRecorder.requestRefunds(List.of(1L, 2L))).thenReturn(List.of(7L));

        listener.onOrderCancelling(new OrderCancellingEvent(List.of(1L, 2L)));

        verify(events).publishEvent(new RefundsRequestedEvent(List.of(7L)));
    }
}
