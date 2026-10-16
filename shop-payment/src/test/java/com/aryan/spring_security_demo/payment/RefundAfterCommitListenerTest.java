package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundAfterCommitListenerTest {

    @Mock private RefundProcessor refundProcessor;
    @InjectMocks private RefundAfterCommitListener listener;

    // The cancellation has committed by now: a failure must not escape (there is
    // nothing to roll back), and must not stop the other refunds.
    @Test
    void refundsEachPayment_andKeepsGoingPastAFailure() {
        when(refundProcessor.refund(7L)).thenThrow(new IllegalStateException("database hiccup"));

        assertThatCode(() -> listener.onRefundsRequested(new RefundsRequestedEvent(List.of(7L, 8L))))
                .doesNotThrowAnyException();

        verify(refundProcessor).refund(8L);
    }
}
