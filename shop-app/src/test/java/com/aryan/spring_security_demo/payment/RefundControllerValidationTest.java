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
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validation slice for {@link RefundController}: a refund is items or an amount,
 * with a reason and an {@code Idempotency-Key}, or it never reaches the service; and
 * the status says whether the request owed a refund (201) or repeated one (200).
 * Security filters are off: who may refund is {@code ShopConfig}'s rule.
 */
@WebMvcTest(RefundController.class)
@AutoConfigureMockMvc(addFilters = false)
class RefundControllerValidationTest {

    private static final String KEY = "00000000-0000-4000-8000-000000000001";
    private static final String ADMIN = "admin@example.com";
    private static final String AMOUNT_BODY = "{\"amount\": 5.00, \"reason\": \"The box arrived damaged\"}";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RefundService refundService;

    @Test
    void neitherItemsNorAnAmount_orBoth_is400() throws Exception {
        for (String body : new String[] {
                "{\"reason\": \"Goodwill\"}",
                "{\"items\": [{\"orderItemId\": 21, \"quantity\": 1}], \"amount\": 5.00, \"reason\": \"Goodwill\"}"}) {
            refund(KEY, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.*", hasItem("give either items or an amount, not both")));
        }
        verifyNeverRefunded();
    }

    @Test
    void restock_withoutItems_is400() throws Exception {
        refund(KEY, "{\"amount\": 5.00, \"restock\": true, \"reason\": \"Goodwill\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.*", hasItem("restock is only for a refund of items")));

        verifyNeverRefunded();
    }

    @Test
    void theSameItemTwice_is400() throws Exception {
        refund(KEY, """
                {"items": [{"orderItemId": 21, "quantity": 1}, {"orderItemId": 21, "quantity": 1}],
                 "reason": "Returned"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.*", hasItem("each order item at most once")));

        verifyNeverRefunded();
    }

    // Malformed input is a 4xx, never a 5xx (the staging fuzzing checks).
    @Test
    void aNullItem_is400() throws Exception {
        refund(KEY, "{\"items\": [null], \"reason\": \"Returned\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.*", hasItem("an item can't be null")));

        verifyNeverRefunded();
    }

    @Test
    void aQuantityBelowOne_is400() throws Exception {
        refund(KEY, "{\"items\": [{\"orderItemId\": 21, \"quantity\": 0}], \"reason\": \"Returned\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.*", hasItem("quantity must be at least 1")));

        verifyNeverRefunded();
    }

    @Test
    void anAmountBelowACent_orWithMoreThanTwoDecimals_is400() throws Exception {
        refund(KEY, "{\"amount\": 0.00, \"reason\": \"Goodwill\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("amount must be at least 0.01"));
        refund(KEY, "{\"amount\": 1.005, \"reason\": \"Goodwill\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("amount has at most 2 decimals"));

        verifyNeverRefunded();
    }

    @Test
    void withoutAReason_is400() throws Exception {
        refund(KEY, "{\"amount\": 5.00, \"reason\": \" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").value("reason is required"));

        verifyNeverRefunded();
    }

    @Test
    void withoutAnIdempotencyKey_orAMalformedOne_is400() throws Exception {
        refund(null, AMOUNT_BODY).andExpect(status().isBadRequest());
        refund("has spaces in it", AMOUNT_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.*",
                        hasItem("Idempotency-Key must be 8 to 64 letters, digits, '-' or '_'")));

        verifyNeverRefunded();
    }

    @Test
    void aNewRefund_is201_withWhereToFindIt() throws Exception {
        when(refundService.refund(eq(9L), eq(KEY), any(RefundOrderRequest.class), eq(ADMIN)))
                .thenReturn(new RefundService.Outcome(refund(), true));

        refund(KEY, AMOUNT_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/orders/9/refunds/11")))
                .andExpect(jsonPath("$.message").value("Refund requested"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void aRepeatedKey_is200_withTheFirstRefund() throws Exception {
        when(refundService.refund(eq(9L), eq(KEY), any(RefundOrderRequest.class), eq(ADMIN)))
                .thenReturn(new RefundService.Outcome(refund(), false));

        refund(KEY, AMOUNT_BODY)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.message").value("Refund already requested"))
                .andExpect(jsonPath("$.data.id").value(11));
    }

    private ResultActions refund(String key, String body) throws Exception {
        var request = post("/api/v1/orders/9/refunds")
                .principal(() -> ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private void verifyNeverRefunded() {
        verify(refundService, never()).refund(anyLong(), anyString(), any(), anyString());
    }

    private static RefundDto refund() {
        return new RefundDto(11L, 7L, Refund.Kind.AMOUNT, new BigDecimal("5.00"), "USD", "The box arrived damaged",
                Refund.Status.PENDING, Instant.parse("2026-10-18T09:00:00Z"), null, List.of());
    }
}
