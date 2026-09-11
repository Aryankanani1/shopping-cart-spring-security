package com.aryan.spring_security_demo.security.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@link RateLimitFilter} throttles the auth endpoints over the real HTTP
 * stack. The {@code test} profile disables rate limiting globally (shared buckets
 * would leak across tests); this class re-enables it with a low capacity via
 * {@link TestPropertySource}, which spins up its own context with fresh buckets.
 *
 * <p>Exercised on {@code /auth/logout} because token revocation is idempotent — an
 * unknown token still returns 200 — so each pre-limit request has a clean outcome
 * that isn't entangled with credential checks or seeded data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.ratelimit.enabled=true",
        "app.ratelimit.capacity=2",
        "app.ratelimit.refill-period=1m"
})
class RateLimitFilterTest {

    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String BODY = "{\"refreshToken\":\"irrelevant-token\"}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("returns 429 with Retry-After once the auth bucket is drained")
    void throttlesAfterCapacity() throws Exception {
        // Capacity is 2: the first two calls pass through (idempotent logout -> 200)
        // and drain the bucket.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(LOGOUT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
        }

        // The third call is throttled before the request is handled.
        mockMvc.perform(post(LOGOUT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Too many requests"));
    }

    @Test
    @DisplayName("does not throttle non-auth endpoints")
    void doesNotThrottleOtherEndpoints() throws Exception {
        // The public health probe shares no bucket with /auth, so calls well beyond
        // the auth capacity keep succeeding.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }
    }
}
