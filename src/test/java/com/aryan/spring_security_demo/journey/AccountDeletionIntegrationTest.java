package com.aryan.spring_security_demo.journey;

import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.model.Notification;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.model.Role;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.model.WishlistItem;
import com.aryan.spring_security_demo.repository.CategoryRepository;
import com.aryan.spring_security_demo.repository.NotificationRepository;
import com.aryan.spring_security_demo.repository.ProductRepository;
import com.aryan.spring_security_demo.repository.RefreshTokenRepository;
import com.aryan.spring_security_demo.repository.RoleRepository;
import com.aryan.spring_security_demo.repository.UserRepository;
import com.aryan.spring_security_demo.repository.WishlistItemRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for {@code DELETE /users/{id}} on a signed-in account. Every
 * signed-in user owns refresh-token rows that reference them by foreign key, and
 * the delete used to trip that constraint (409) instead of removing the account.
 * Runs the full HTTP → security → service → JPA stack against H2 ({@code test}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountDeletionIntegrationTest {

    private static final String EMAIL = "leaving@example.com";
    private static final String PASSWORD = "secret123";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private WishlistItemRepository wishlistItemRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long userId;

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
        userId = userRepository.save(user).getId();
    }

    @Test
    @DisplayName("a signed-in user can delete their own account, and its sessions go with it")
    void deleteOwnAccount_whileSignedIn() throws Exception {
        JsonNode session = login();
        assertThat(refreshTokenRepository.count()).as("login left a token row").isEqualTo(1);

        mockMvc.perform(delete("/api/v1/users/" + userId)
                        .header("Authorization", "Bearer " + session.path("token").asText()))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(refreshTokenRepository.count()).isZero();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(session.path("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an account with a wishlist and notifications can be deleted; they go with it")
    void deleteOwnAccount_withWishlistAndNotifications() throws Exception {
        Category category = categoryRepository.existsByName("Electronics")
                ? categoryRepository.findByName("Electronics")
                : categoryRepository.save(new Category("Electronics"));
        Product product = productRepository.save(new Product(
                "Desk Lamp", new BigDecimal("40.00"), "", "Acme", 3, category));
        User user = userRepository.findById(userId).orElseThrow();
        WishlistItem item = wishlistItemRepository.save(new WishlistItem(user, product, Instant.now()));
        notificationRepository.save(Notification.reminder(item, Instant.now()));

        mockMvc.perform(delete("/api/v1/users/" + userId)
                        .header("Authorization", "Bearer " + login().path("token").asText()))
                .andExpect(status().isNoContent());

        assertThat(wishlistItemRepository.count()).isZero();
        assertThat(notificationRepository.count()).isZero();
        assertThat(productRepository.existsById(product.getId())).as("the product itself stays").isTrue();
        productRepository.delete(product);
    }

    private JsonNode login() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }
}
