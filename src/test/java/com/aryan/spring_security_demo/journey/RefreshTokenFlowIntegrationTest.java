package com.aryan.spring_security_demo.journey;

import com.aryan.spring_security_demo.identity.InvalidRefreshTokenException;
import com.aryan.spring_security_demo.identity.RefreshTokenRepository;
import com.aryan.spring_security_demo.identity.RefreshTokenService;
import com.aryan.spring_security_demo.identity.Role;
import com.aryan.spring_security_demo.identity.RoleRepository;
import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.UserRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
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
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private PlatformTransactionManager transactionManager;

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
    @DisplayName("replaying a spent refresh token revokes the newer one too")
    void refresh_replayRevokesWholeFamily() throws Exception {
        String original = login().path("refreshToken").asText();
        String current = refresh(original).path("refreshToken").asText();

        // Someone replays the spent token: a sign it leaked.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(original)))
                .andExpect(status().isUnauthorized());

        // So the newest token — possibly the thief's — no longer works either.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(current)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("two refreshes racing with one token: only one rotates, and the race counts as reuse")
    void refresh_racingWithTheSameToken_onlyOneRotates() throws Exception {
        String original = login().path("refreshToken").asText();
        TransactionTemplate requestB = new TransactionTemplate(transactionManager);
        TransactionTemplate requestA = new TransactionTemplate(transactionManager);
        requestA.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Two requests present the same token at once: B reads it while it is
        // still active, A rotates it and commits, then B carries on from its read.
        // B's persistence context keeps the token as B first read it, which
        // replays that interleaving on one thread.
        AtomicReference<String> tokenGivenToA = new AtomicReference<>();
        Throwable outcomeForB = requestB.execute(status -> {
            refreshTokenRepository.findAll();  // B reads the token
            tokenGivenToA.set(requestA.execute(s -> refreshTokenService.rotate(original)).rawRefreshToken());
            return catchThrowable(() -> refreshTokenService.rotate(original));
        });

        // B gets no second session out of the same token...
        assertThat(outcomeForB).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(refreshTokenRepository.count()).as("original + A's token only").isEqualTo(2);

        // ...and since the token was used twice, A's token is revoked too.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokenGivenToA.get())))
                .andExpect(status().isUnauthorized());
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
