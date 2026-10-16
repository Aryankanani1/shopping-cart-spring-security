package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.order.OrderServiceInterface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The three steps of a payment and what each outcome records, with the recorder
 * and the provider mocked.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long ORDER_ID = 9L;
    private static final Long PAYMENT_ID = 7L;
    private static final String KEY = "key-12345";
    private static final ChargeRequest CHARGE =
            new ChargeRequest("pay_7", new BigDecimal("59.97"), "USD", FakePaymentGateway.VISA);

    @Mock private PaymentRecorder recorder;
    @Mock private PaymentGatewayClient gateway;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderServiceInterface orderService;

    @InjectMocks private PaymentService paymentService;

    @Test
    void pay_recordsTheAttempt_chargesIt_thenRecordsTheApproval() {
        ChargeResult.Approved approved = new ChargeResult.Approved("ch_1", "Visa", "4242");
        when(recorder.start(ORDER_ID, KEY, FakePaymentGateway.VISA))
                .thenReturn(new PaymentRecorder.Attempt(payment(PaymentStatus.PROCESSING), false));
        when(gateway.charge(CHARGE)).thenReturn(approved);
        when(recorder.recordSuccess(PAYMENT_ID, approved)).thenReturn(payment(PaymentStatus.SUCCEEDED));

        PaymentServiceInterface.PayResult result = paymentService.pay(ORDER_ID, KEY, FakePaymentGateway.VISA);

        assertThat(result.created()).isTrue();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        InOrder steps = inOrder(recorder, gateway);
        steps.verify(recorder).start(ORDER_ID, KEY, FakePaymentGateway.VISA);
        steps.verify(gateway).charge(CHARGE);
        steps.verify(recorder).recordSuccess(PAYMENT_ID, approved);
    }

    @Test
    void pay_declined_recordsTheFailureWithTheProvidersReason() {
        when(recorder.start(ORDER_ID, KEY, FakePaymentGateway.VISA))
                .thenReturn(new PaymentRecorder.Attempt(payment(PaymentStatus.PROCESSING), false));
        when(gateway.charge(CHARGE)).thenReturn(new ChargeResult.Declined("Your card was declined."));
        when(recorder.recordFailure(PAYMENT_ID, "Your card was declined.")).thenReturn(payment(PaymentStatus.FAILED));

        PaymentServiceInterface.PayResult result = paymentService.pay(ORDER_ID, KEY, FakePaymentGateway.VISA);

        assertThat(result.created()).isTrue();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void pay_providerStillUnavailableAfterRetries_recordsAFailure() {
        when(recorder.start(ORDER_ID, KEY, FakePaymentGateway.VISA))
                .thenReturn(new PaymentRecorder.Attempt(payment(PaymentStatus.PROCESSING), false));
        when(gateway.charge(CHARGE)).thenThrow(new PaymentProviderUnavailableException("down"));
        when(recorder.recordFailure(PAYMENT_ID, PaymentService.PROVIDER_UNAVAILABLE))
                .thenReturn(payment(PaymentStatus.FAILED));

        assertThat(paymentService.pay(ORDER_ID, KEY, FakePaymentGateway.VISA).payment().status())
                .isEqualTo(PaymentStatus.FAILED);
    }

    // A timeout once the request was sent: it may have charged, so it isn't failed.
    // The attempt stays PROCESSING for PaymentReconciler.
    @Test
    void pay_outcomeUnknown_leavesTheAttemptProcessing() {
        when(recorder.start(ORDER_ID, KEY, FakePaymentGateway.VISA))
                .thenReturn(new PaymentRecorder.Attempt(payment(PaymentStatus.PROCESSING), false));
        when(gateway.charge(CHARGE)).thenThrow(new PaymentOutcomeUnknownException("timed out"));

        PaymentServiceInterface.PayResult result = paymentService.pay(ORDER_ID, KEY, FakePaymentGateway.VISA);

        assertThat(result.created()).isTrue();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.PROCESSING);
        verify(recorder, never()).recordFailure(anyLong(), anyString());
        verify(recorder, never()).recordSuccess(anyLong(), any());
    }

    // An answer we can't interpret at all (a bug)
    // leaves the payment PROCESSING: the provider may have charged, so it is not
    // marked failed, and the order can't be paid again or cancelled meanwhile.
    @Test
    void pay_unexpectedProviderError_recordsNoOutcome() {
        when(recorder.start(ORDER_ID, KEY, FakePaymentGateway.VISA))
                .thenReturn(new PaymentRecorder.Attempt(payment(PaymentStatus.PROCESSING), false));
        when(gateway.charge(CHARGE)).thenThrow(new IllegalStateException("timed out"));

        assertThatThrownBy(() -> paymentService.pay(ORDER_ID, KEY, FakePaymentGateway.VISA))
                .isInstanceOf(IllegalStateException.class);

        verify(recorder, never()).recordFailure(anyLong(), anyString());
        verify(recorder, never()).recordSuccess(anyLong(), any());
    }

    @Test
    void pay_repeatedKey_returnsTheFirstAttempt_withoutCharging() {
        when(recorder.start(ORDER_ID, KEY, FakePaymentGateway.VISA))
                .thenReturn(new PaymentRecorder.Attempt(payment(PaymentStatus.SUCCEEDED), true));

        PaymentServiceInterface.PayResult result = paymentService.pay(ORDER_ID, KEY, FakePaymentGateway.VISA);

        assertThat(result.created()).isFalse();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verifyNoInteractions(gateway);
    }

    @Test
    void getPayments_checksAccessToTheOrder_first() {
        doThrow(new AccessDeniedException("not yours")).when(orderService).getOrder(ORDER_ID);

        assertThatThrownBy(() -> paymentService.getPayments(ORDER_ID)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void getPayments_listsTheOrdersAttempts() {
        when(paymentRepository.findAllByOrder(ORDER_ID)).thenReturn(List.of());

        assertThat(paymentService.getPayments(ORDER_ID)).isEmpty();

        verify(orderService).getOrder(ORDER_ID);
    }

    @Test
    void getPayment_ofAnotherOrder_is404() {
        when(paymentRepository.findByIdAndOrder(PAYMENT_ID, ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(ORDER_ID, PAYMENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(orderService).getOrder(ORDER_ID);
    }

    private static PaymentDto payment(PaymentStatus status) {
        return new PaymentDto(PAYMENT_ID, ORDER_ID, status, new BigDecimal("59.97"), "USD",
                FakePaymentGateway.VISA, null, null, null, null, Instant.parse("2026-10-15T12:00:00Z"), null,
                null, null, null);
    }
}
