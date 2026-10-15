package com.aryan.spring_security_demo.journey;

import com.aryan.spring_security_demo.cart.CartRepository;
import com.aryan.spring_security_demo.identity.RefreshTokenRepository;
import com.aryan.spring_security_demo.identity.Role;
import com.aryan.spring_security_demo.identity.RoleRepository;
import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Passwords are hashed and checked with no database transaction open. BCrypt is
 * slow on purpose, and a transaction holds a pooled connection until it ends, so
 * hashing inside one kept a connection busy for the whole hash. The encoder is
 * spied on to record, at each hash and check, whether a transaction is active.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordHashingIntegrationTest {

    private static final String EMAIL = "hasher@example.com";
    private static final String PASSWORD = "secret123";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<String> callsInATransaction = new CopyOnWriteArrayList<>();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @MockitoSpyBean private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();  // FK on users — clear before the users
        cartRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER")));

        doAnswer(inv -> record("encode", inv)).when(passwordEncoder).encode(any());
        doAnswer(inv -> record("matches", inv)).when(passwordEncoder).matches(any(), any());
    }

    @Test
    @DisplayName("sign-up hashes the password with no transaction open")
    void signUp_hashesOutsideAnyTransaction() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Ada", "lastName": "Lovelace", "email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated());

        assertThat(calls).contains("encode");
        assertThat(callsInATransaction).as("encoder calls made inside a transaction").isEmpty();
    }

    @Test
    @DisplayName("changing the password checks and hashes with no transaction open")
    void changePassword_hashesOutsideAnyTransaction() throws Exception {
        saveUserWithPassword(PASSWORD);
        String token = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data").path("token").asText();
        calls.clear();  // only the password change from here on

        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "n3w-secret!"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk());

        assertThat(calls).contains("matches", "encode");
        assertThat(callsInATransaction).as("encoder calls made inside a transaction").isEmpty();
    }

    private Object record(String call, InvocationOnMock invocation) throws Throwable {
        calls.add(call);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            callsInATransaction.add(call);
        }
        return invocation.callRealMethod();
    }

    private void saveUserWithPassword(String password) {
        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setEmail(EMAIL);
        user.setPassword(passwordEncoder.encode(password));
        user.setRoles(Set.of(roleRepository.findByName("ROLE_CUSTOMER").orElseThrow()));
        userRepository.save(user);
    }
}
