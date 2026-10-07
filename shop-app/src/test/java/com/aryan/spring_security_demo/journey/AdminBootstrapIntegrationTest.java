package com.aryan.spring_security_demo.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Outside dev, ADMIN_EMAIL and ADMIN_PASSWORD are the only way to get an admin:
 * the account is created at startup, can sign in, and passes the admin-only rules.
 */
@SpringBootTest(properties = {
        "app.bootstrap.admin.email=" + AdminBootstrapIntegrationTest.EMAIL,
        "app.bootstrap.admin.password=" + AdminBootstrapIntegrationTest.PASSWORD})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBootstrapIntegrationTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "a-long-admin-password";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("the bootstrapped admin can sign in and use the admin-only endpoints")
    void bootstrappedAdmin_signsInAndPassesTheAdminRules() throws Exception {
        String token = login(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/orders/admin").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a customer who signs up still gets 403 on the admin-only endpoints")
    void signedUpCustomer_isStillRefused() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Ada", "lastName": "L", "email": "ada-bootstrap@example.com",
                                 "password": "secret123"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/orders/admin")
                        .header("Authorization", "Bearer " + login("ada-bootstrap@example.com", "secret123")))
                .andExpect(status().isForbidden());
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("token").asText();
    }
}
