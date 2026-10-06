package com.aryan.spring_security_demo.journey;

import com.aryan.spring_security_demo.catalog.Category;
import com.aryan.spring_security_demo.catalog.CategoryRepository;
import com.aryan.spring_security_demo.catalog.Product;
import com.aryan.spring_security_demo.catalog.ProductRepository;
import com.aryan.spring_security_demo.identity.RefreshTokenRepository;
import com.aryan.spring_security_demo.identity.Role;
import com.aryan.spring_security_demo.identity.RoleRepository;
import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.UserRepository;
import com.aryan.spring_security_demo.identity.security.user.UserDetails;
import com.aryan.spring_security_demo.notification.Notification;
import com.aryan.spring_security_demo.notification.NotificationRepository;
import com.aryan.spring_security_demo.order.Order;
import com.aryan.spring_security_demo.order.OrderItem;
import com.aryan.spring_security_demo.order.OrderRepository;
import com.aryan.spring_security_demo.order.OrderStatus;
import com.aryan.spring_security_demo.wishlist.WishlistItem;
import com.aryan.spring_security_demo.wishlist.WishlistItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @Autowired private OrderRepository orderRepository;
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
    @DisplayName("the deleted account's unexpired access token is treated as signed out, not a 500")
    void deletedAccountToken_isAnonymous() throws Exception {
        String bearer = "Bearer " + login().path("token").asText();
        mockMvc.perform(delete("/api/v1/users/" + userId).header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products").header("Authorization", bearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/" + userId).header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an account with a wishlist and notifications can be deleted; they go with it")
    void deleteOwnAccount_withWishlistAndNotifications() throws Exception {
        Product product = productRepository.save(new Product(
                "Desk Lamp", new BigDecimal("40.00"), "", "Acme", 3, electronics()));
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

    // Regression: the account's orders were deleted with it (a JPA cascade), and
    // the units of the open ones never went back into stock.
    @Test
    @DisplayName("an account with orders can be deleted; the orders stay, and the open ones are cancelled and restocked")
    void deleteOwnAccount_withOrders() throws Exception {
        // 3 left in stock after these two orders took 2 and 1.
        Product product = productRepository.save(new Product(
                "Kettle", new BigDecimal("25.00"), "", "Acme", 3, electronics()));
        User user = userRepository.findById(userId).orElseThrow();
        Long open = orderRepository.save(order(user, product, 2, OrderStatus.PENDING)).getId();
        Long delivered = orderRepository.save(order(user, product, 1, OrderStatus.DELIVERED)).getId();

        mockMvc.perform(delete("/api/v1/users/" + userId)
                        .header("Authorization", "Bearer " + login().path("token").asText()))
                .andExpect(status().isNoContent());

        Order cancelled = orderRepository.findByIdWithItems(open).orElseThrow();
        assertThat(cancelled.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getUser()).isNull();
        assertThat(cancelled.getOrderItems()).hasSize(1);
        Order kept = orderRepository.findByIdWithItems(delivered).orElseThrow();
        assertThat(kept.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(kept.getUser()).isNull();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getInventory())
                .as("the open order's 2 units are back in stock").isEqualTo(5);

        // Admins still see both orders, with no customer.
        JsonNode rows = objectMapper.readTree(mockMvc.perform(get("/api/v1/orders/admin")
                        .param("size", "100").with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data").path("content");
        for (Long id : List.of(open, delivered)) {
            JsonNode row = StreamSupport.stream(rows.spliterator(), false)
                    .filter(r -> r.path("id").asLong() == id)
                    .findFirst().orElseThrow(() -> new AssertionError("order " + id + " missing from the admin list"));
            assertThat(row.path("userId").isNull()).isTrue();
            assertThat(row.path("userEmail").isNull()).isTrue();
        }
        // An ownerless order fails the ownership check for everyone, admins included:
        // a 403, not the NullPointerException (500) it used to be.
        mockMvc.perform(post("/api/v1/orders/" + delivered + "/cancel").with(admin()))
                .andExpect(status().isForbidden());

        orderRepository.deleteAllById(List.of(open, delivered));
        productRepository.deleteById(product.getId());  // by id: the restock bumped its version
    }

    private Category electronics() {
        return categoryRepository.existsByName("Electronics")
                ? categoryRepository.findByName("Electronics")
                : categoryRepository.save(new Category("Electronics"));
    }

    private static Order order(User user, Product product, int quantity, OrderStatus status) {
        Order order = new Order();
        order.setUser(user);
        order.setOrderStatus(status);
        order.setLocalDate(LocalDate.of(2026, 9, 14));
        order.addOrderItem(new OrderItem(product, quantity, product.getPrice()));
        order.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return order;
    }

    private static RequestPostProcessor admin() {
        UserDetails admin = new UserDetails(-1L, "admin@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return authentication(new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
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
