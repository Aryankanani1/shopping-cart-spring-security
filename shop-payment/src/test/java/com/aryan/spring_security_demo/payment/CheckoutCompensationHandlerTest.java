package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CheckoutCompensationHandlerTest {

    private final CheckoutSaga checkoutSaga = mock(CheckoutSaga.class);
    private final CheckoutCompensationHandler handler = new CheckoutCompensationHandler(checkoutSaga);

    @Test
    void takesCheckoutCompensations_andCancelsTheOrder() {
        assertThat(handler.type()).isEqualTo("checkout.compensate");

        assertThat(handler.handle(9L)).isTrue();

        verify(checkoutSaga).compensate(9L);
    }
}
