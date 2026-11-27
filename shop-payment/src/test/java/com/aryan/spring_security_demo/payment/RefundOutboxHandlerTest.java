package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefundOutboxHandlerTest {

    private final RefundProcessor processor = mock(RefundProcessor.class);
    private final RefundOutboxHandler handler = new RefundOutboxHandler(processor);

    @Test
    void takesRefundMessages() {
        assertThat(handler.type()).isEqualTo("payment.refund");
    }

    // Still owed (the provider is down): the message is delivered again later.
    @Test
    void sendsTheRefund_andIsDoneOnlyOnceItIsRefunded() {
        when(processor.refund(11L)).thenReturn(true);
        when(processor.refund(12L)).thenReturn(false);

        assertThat(handler.handle(11L)).isTrue();
        assertThat(handler.handle(12L)).isFalse();
    }
}
