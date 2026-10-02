package com.aryan.spring_security_demo.security;

import com.aryan.spring_security_demo.identity.Role;
import com.aryan.spring_security_demo.identity.RoleRepository;
import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With the actuator on its own port ({@code MANAGEMENT_SERVER_PORT}), the same
 * access rules apply there: the security filter chain, JWT authentication
 * included, runs on the management port too. Real HTTP on both ports.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles("test")
class ActuatorManagementPortTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort private int appPort;
    @LocalManagementPort private int managementPort;

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("the management port serves the actuator under the same rules")
    void managementPort_enforcesTheSameRules() throws Exception {
        assertThat(managementPort).isNotEqualTo(appPort);

        assertThat(get("/actuator/health", null).statusCode()).isEqualTo(200);
        assertThat(get("/actuator/metrics", null).statusCode()).isEqualTo(401);
        assertThat(get("/actuator/metrics", tokenFor("ROLE_CUSTOMER")).statusCode()).isEqualTo(403);
        assertThat(get("/actuator/metrics", tokenFor("ROLE_ADMIN")).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** A fresh user with the role, signed in through the app port. */
    private String tokenFor(String roleName) throws Exception {
        Role role = roleRepository.findByName(roleName).orElseGet(() -> roleRepository.save(new Role(roleName)));
        User user = new User();
        user.setFirstName("Ops");
        user.setLastName("Check");
        user.setEmail("ops-" + UUID.randomUUID() + "@example.com");
        user.setPassword(passwordEncoder.encode("secret123"));
        user.setRoles(Set.of(role));
        userRepository.save(user);

        HttpRequest login = HttpRequest.newBuilder(URI.create("http://localhost:" + appPort + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + user.getEmail() + "\",\"password\":\"secret123\"}"))
                .build();
        HttpResponse<String> response = http.send(login, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body()).path("data").path("token").asText();
    }
}
