package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validation slice for {@link PaymentController}: a request without a usable
 * {@code Idempotency-Key} or payment method never reaches the service, and the
 * status says whether the request started an attempt (201) or repeated one (200).
 * Security filters are off; ownership is the service's check.
 */
@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerValidationTest {

    private static final String KEY = "00000000-0000-4000-8000-000000000001";
    private static final String VISA_BODY = "{\"paymentMethod\":\"test_visa\"}";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PaymentServiceInterface paymentService;

    @Test
    void pay_withoutAnIdempotencyKey_is400() throws Exception {
        pay(null, VISA_BODY).andExpect(status().isBadRequest());

        verify(paymentService, never()).pay(anyLong(), anyString(), anyString());
    }

    @Test
    void pay_withAMalformedIdempotencyKey_is400() throws Exception {
        for (String key : new String[] {"short", "has spaces in it", "x".repeat(65)}) {
            pay(key, VISA_BODY)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.*",
                            hasItem("Idempotency-Key must be 8 to 64 letters, digits, '-' or '_'")));
        }
        verify(paymentService, never()).pay(anyLong(), anyString(), anyString());
    }

    @Test
    void pay_withoutAPaymentMethod_is400() throws Exception {
        pay(KEY, "{\"paymentMethod\":\" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paymentMethod").value("paymentMethod is required"));

        verify(paymentService, never()).pay(anyLong(), anyString(), anyString());
    }

    @Test
    void pay_aNewAttempt_is201_withWhereToFindIt() throws Exception {
        when(paymentService.pay(9L, KEY, "test_visa"))
                .thenReturn(new PaymentServiceInterface.PayResult(payment(PaymentStatus.SUCCEEDED), true));

        pay(KEY, VISA_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/orders/9/payments/7")))
                .andExpect(jsonPath("$.message").value("Payment succeeded"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.amount").value(59.97));
    }

    @Test
    void pay_aDeclinedAttempt_is201_withTheReason() throws Exception {
        when(paymentService.pay(9L, KEY, "test_visa"))
                .thenReturn(new PaymentServiceInterface.PayResult(payment(PaymentStatus.FAILED), true));

        pay(KEY, VISA_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Payment failed"));
    }

    // The provider didn't answer in time: accepted, and settled later by reconciliation.
    @Test
    void pay_aNewAttemptStillProcessing_is202_withWhereToFindIt() throws Exception {
        when(paymentService.pay(9L, KEY, "test_visa"))
                .thenReturn(new PaymentServiceInterface.PayResult(payment(PaymentStatus.PROCESSING), true));

        pay(KEY, VISA_BODY)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", endsWith("/api/v1/orders/9/payments/7")))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void pay_aRepeatedKey_is200_withTheFirstAnswer() throws Exception {
        when(paymentService.pay(9L, KEY, "test_visa"))
                .thenReturn(new PaymentServiceInterface.PayResult(payment(PaymentStatus.PROCESSING), false));

        pay(KEY, VISA_BODY)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.message").value("Payment is being processed"));
    }

    private ResultActions pay(String key, String body) throws Exception {
        var request = post("/api/v1/orders/9/payments").contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private static PaymentDto payment(PaymentStatus status) {
        return new PaymentDto(7L, 9L, status, new BigDecimal("59.97"), "USD", "test_visa",
                null, null, null, null, Instant.parse("2026-10-15T12:00:00Z"), null, null, null, null);
    }
}
