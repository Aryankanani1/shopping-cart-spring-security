package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.OrderCancellingEvent;
import com.aryan.spring_security_demo.order.OrderPlacedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** The order module's events, handed to the checkout saga. */
@ExtendWith(MockitoExtension.class)
class PaymentOrderListenerTest {

    @Mock private CheckoutSaga checkoutSaga;
    @InjectMocks private PaymentOrderListener listener;

    @Test
    void anOrderPlaced_startsItsCheckoutSaga() {
        listener.onOrderPlaced(new OrderPlacedEvent(1L));

        verify(checkoutSaga).orderBooked(1L);
    }

    @Test
    void ordersBeingCancelled_areCompensated_andTheSagaCanRefuse() {
        doThrow(new PaymentInProgressException("being paid for")).when(checkoutSaga).orderCancelling(List.of(1L, 2L));

        assertThatThrownBy(() -> listener.onOrderCancelling(new OrderCancellingEvent(List.of(1L, 2L))))
                .isInstanceOf(PaymentInProgressException.class);
    }
}
