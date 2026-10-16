package com.aryan.spring_security_demo.journey;

import com.aryan.spring_security_demo.identity.AdminBootstrap;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Outside dev, ADMIN_EMAIL and ADMIN_PASSWORD are the only way to get an admin:
 * the account is created at startup, can sign in, and passes the admin-only rules.
 * Every restart runs the bootstrap again, and finds the admin as it is.
 */
@Tag("integration")
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
    @Autowired private AdminBootstrap adminBootstrap;
    @Autowired private JdbcTemplate jdbcTemplate;

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

    @Test
    @DisplayName("a restart finds the admin and gives them no second copy of the admin role")
    void restart_addsNoSecondAdminRole() {
        // The context's start created the admin; each restart runs the bootstrap again.
        adminBootstrap.onApplicationEvent(null);
        adminBootstrap.onApplicationEvent(null);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from user_roles where user_id = (select id from users where email = ?)",
                Integer.class, EMAIL)).isEqualTo(1);
    }

    @Test
    @DisplayName("the database refuses the same role twice for one user")
    void userRoles_refusesADuplicateRow() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        insert into user_roles (user_id, role_id)
                        select user_id, role_id from user_roles
                        where user_id = (select id from users where email = ?)""", EMAIL))
                .isInstanceOf(DuplicateKeyException.class);
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
