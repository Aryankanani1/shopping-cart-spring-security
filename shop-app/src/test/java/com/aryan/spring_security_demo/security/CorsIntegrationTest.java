package com.aryan.spring_security_demo.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A frontend on another origin, listed in app.cors.allowed-origins, can call the
 * API from a browser; any other origin is refused, and the CORS headers stay off
 * everything but the API.
 */
@Tag("integration")
@SpringBootTest(properties = "app.cors.allowed-origins=" + CorsIntegrationTest.SHOP)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsIntegrationTest {

    static final String SHOP = "https://shop.example.com";
    private static final String OTHER = "https://other.example.com";

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("a preflight from the listed origin is answered, even for an endpoint that needs a token")
    void preflightFromTheListedOrigin_isAllowed() throws Exception {
        mockMvc.perform(options("/api/v1/orders")
                        .header("Origin", SHOP)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization, content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", SHOP))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));
    }

    @Test
    @DisplayName("a request from the listed origin gets the CORS header")
    void requestFromTheListedOrigin_carriesTheHeader() throws Exception {
        mockMvc.perform(get("/api/v1/categories").header("Origin", SHOP))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", SHOP));
    }

    @Test
    @DisplayName("another origin is refused, preflight and request alike")
    void anotherOrigin_isRefused() throws Exception {
        mockMvc.perform(options("/api/v1/products")
                        .header("Origin", OTHER)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(get("/api/v1/categories").header("Origin", OTHER))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("the actuator gets no CORS, even from the listed origin")
    void actuator_getsNoCors() throws Exception {
        mockMvc.perform(get("/actuator/health").header("Origin", SHOP))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
