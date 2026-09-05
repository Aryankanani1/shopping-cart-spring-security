package com.aryan.spring_security_demo.journey;

import com.aryan.spring_security_demo.model.Role;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.repository.RefreshTokenRepository;
import com.aryan.spring_security_demo.repository.RoleRepository;
import com.aryan.spring_security_demo.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the refresh-token lifecycle: login issues an access +
 * refresh pair, /auth/refresh rotates the refresh token, a rotated-away token is
 * dead (reuse detection), and /auth/logout revokes the session. Runs the full
 * HTTP → security → service → JPA stack against H2 (the {@code test} profile).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenFlowIntegrationTest {

    private static final String EMAIL = "shopper@example.com";
    private static final String PASSWORD = "secret123";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();  // FK on users — clear before the users
        userRepository.deleteAll();

        Role customer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER")));

        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setEmail(EMAIL);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRoles(Set.of(customer));
        userRepository.save(user);
    }

    @Test
    @DisplayName("login issues both an access token and a refresh token")
    void login_issuesAccessAndRefreshTokens() throws Exception {
        JsonNode data = login();
        assertThat(data.path("token").asText()).as("access token").isNotEmpty();
        assertThat(data.path("refreshToken").asText()).as("refresh token").isNotEmpty();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("refresh rotates: a new access + new refresh token are returned")
    void refresh_rotatesToken() throws Exception {
        String refreshToken = login().path("refreshToken").asText();

        JsonNode refreshed = refresh(refreshToken);
        assertThat(refreshed.path("token").asText()).as("new access token").isNotEmpty();
        String newRefresh = refreshed.path("refreshToken").asText();
        assertThat(newRefresh).as("rotated refresh token differs").isNotEqualTo(refreshToken);
    }

    @Test
    @DisplayName("a rotated-away refresh token is revoked and cannot be reused")
    void refresh_oldTokenIsRevokedAfterRotation() throws Exception {
        String original = login().path("refreshToken").asText();
        refresh(original);  // rotates: original is now revoked

        // Replaying the spent token is rejected (and trips reuse detection).
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(original)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("logout revokes the refresh token so it can no longer be exchanged")
    void logout_revokesRefreshToken() throws Exception {
        String refreshToken = login().path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh with an unknown token returns 401")
    void refresh_withUnknownToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("not-a-real-token")))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers -----------------------------------------------------------

    private JsonNode login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result);
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Token refreshed"))
                .andReturn();
        return dataOf(result);
    }

    private String refreshBody(String refreshToken) throws Exception {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
