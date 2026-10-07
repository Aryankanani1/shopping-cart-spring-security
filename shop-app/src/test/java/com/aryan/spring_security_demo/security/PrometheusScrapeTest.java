package com.aryan.spring_security_demo.security;

import com.aryan.spring_security_demo.identity.RefreshTokenRepository;
import com.aryan.spring_security_demo.identity.Role;
import com.aryan.spring_security_demo.identity.RoleRepository;
import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With the scrape account on, Prometheus reads /actuator/prometheus with HTTP
 * Basic. The account works there and nowhere else, and no other credentials
 * work there.
 */
@Tag("integration")
@SpringBootTest(properties = {
        "app.metrics.prometheus.scrape-enabled=true",
        "app.metrics.prometheus.username=prometheus",
        "app.metrics.prometheus.password=" + PrometheusScrapeTest.PASSWORD,
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrometheusScrapeTest {

    static final String PASSWORD = "test-scrape-password-0123";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("the scrape account reads the metrics in Prometheus format")
    void scrapeAccount_readsMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(httpBasic("prometheus", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("jvm_memory_used_bytes{")));
    }

    @Test
    @DisplayName("no or wrong credentials: 401 with a Basic challenge")
    void missingOrWrongCredentials_areRejected() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Basic")));
        mockMvc.perform(get("/actuator/prometheus").with(httpBasic("prometheus", "not-the-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the scrape account opens nothing else: other actuator endpoints and the API")
    void scrapeAccount_worksOnlyOnPrometheus() throws Exception {
        mockMvc.perform(get("/actuator/metrics").with(httpBasic("prometheus", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/info").with(httpBasic("prometheus", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/orders/admin").with(httpBasic("prometheus", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an admin's own email and password don't work as scrape credentials")
    void adminCredentials_doNotWorkHere() throws Exception {
        Role admin = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
        User user = new User();
        user.setFirstName("Ops");
        user.setLastName("Admin");
        user.setEmail("ops-" + UUID.randomUUID() + "@example.com");
        user.setPassword(passwordEncoder.encode("admin-password-123"));
        user.setRoles(Set.of(admin));
        userRepository.save(user);

        mockMvc.perform(get("/actuator/prometheus").with(httpBasic(user.getEmail(), "admin-password-123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("while scraping is on, an admin session doesn't open the endpoint either")
    void adminSession_isNotTheScrapeAccount() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
    }
}
