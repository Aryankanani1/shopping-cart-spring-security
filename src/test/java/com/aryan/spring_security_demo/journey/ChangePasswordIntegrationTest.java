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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of {@code PUT /auth/password}: the current password is
 * re-verified, the credentials swap, every pre-existing session ends, and the
 * caller keeps a working session via the fresh token pair it gets back. Runs the
 * full HTTP → security → service → JPA stack against H2 (the {@code test} profile).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChangePasswordIntegrationTest {

    private static final String EMAIL = "rotator@example.com";
    private static final String OLD_PASSWORD = "secret123";
    private static final String NEW_PASSWORD = "n3w-secret!";

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
        user.setPassword(passwordEncoder.encode(OLD_PASSWORD));
        user.setRoles(Set.of(customer));
        userRepository.save(user);
    }

    @Test
    @DisplayName("correct current password: credentials swap and a fresh token pair is returned")
    void changePassword_swapsCredentials() throws Exception {
        JsonNode session = login(OLD_PASSWORD);

        JsonNode changed = dataOf(changePassword(session.path("token").asText(), OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed")));
        assertThat(changed.path("token").asText()).as("new access token").isNotEmpty();
        assertThat(changed.path("refreshToken").asText()).as("new refresh token").isNotEmpty();

        loginExpecting(OLD_PASSWORD).andExpect(status().isUnauthorized());
        loginExpecting(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("every earlier session ends, and replaying one doesn't kill the caller's new session")
    void changePassword_endsExistingSessions() throws Exception {
        String otherDevice = login(OLD_PASSWORD).path("refreshToken").asText();
        JsonNode caller = login(OLD_PASSWORD);

        JsonNode changed = dataOf(changePassword(caller.path("token").asText(), OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk()));

        // Both pre-change refresh tokens are dead...
        refresh(otherDevice).andExpect(status().isUnauthorized());
        refresh(caller.path("refreshToken").asText()).andExpect(status().isUnauthorized());
        // ...and presenting them must not have tripped reuse detection, which would
        // have revoked the caller's fresh token along with them.
        refresh(changed.path("refreshToken").asText()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("wrong current password: 400 on currentPassword, nothing changes")
    void changePassword_wrongCurrentPassword_isRejected() throws Exception {
        JsonNode session = login(OLD_PASSWORD);

        changePassword(session.path("token").asText(), "not-my-password", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").value("Current password is incorrect"));

        loginExpecting(OLD_PASSWORD).andExpect(status().isOk());
        // A failed attempt must not end the caller's session.
        refresh(session.path("refreshToken").asText()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("new password equal to the current one: 400 on newPassword")
    void changePassword_unchangedPassword_isRejected() throws Exception {
        String token = login(OLD_PASSWORD).path("token").asText();

        changePassword(token, OLD_PASSWORD, OLD_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    @DisplayName("new password shorter than the minimum: 400 validation error")
    void changePassword_tooShort_isRejected() throws Exception {
        String token = login(OLD_PASSWORD).path("token").asText();

        changePassword(token, OLD_PASSWORD, "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    @DisplayName("no bearer token: 401 even though the path is under /auth")
    void changePassword_unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers -----------------------------------------------------------

    private JsonNode login(String password) throws Exception {
        return dataOf(loginExpecting(password).andExpect(status().isOk()));
    }

    private ResultActions loginExpecting(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(EMAIL, password)));
    }

    private ResultActions changePassword(String accessToken, String current, String next) throws Exception {
        return mockMvc.perform(put("/api/v1/auth/password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(changeBody(current, next)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken)));
    }

    private String changeBody(String current, String next) {
        return """
                {"currentPassword": "%s", "newPassword": "%s"}
                """.formatted(current, next);
    }

    private JsonNode dataOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }
}
