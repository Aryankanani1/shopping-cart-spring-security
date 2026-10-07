package com.aryan.spring_security_demo.journey;

import com.aryan.spring_security_demo.cart.CartItemRepository;
import com.aryan.spring_security_demo.cart.CartRepository;
import com.aryan.spring_security_demo.catalog.Category;
import com.aryan.spring_security_demo.catalog.CategoryRepository;
import com.aryan.spring_security_demo.catalog.Product;
import com.aryan.spring_security_demo.catalog.ProductRepository;
import com.aryan.spring_security_demo.identity.RefreshTokenRepository;
import com.aryan.spring_security_demo.identity.Role;
import com.aryan.spring_security_demo.identity.RoleRepository;
import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.UserRepository;
import com.aryan.spring_security_demo.notification.NotificationRepository;
import com.aryan.spring_security_demo.order.OrderRepository;
import com.aryan.spring_security_demo.wishlist.WishlistAlertService;
import com.aryan.spring_security_demo.wishlist.WishlistItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the wishlist API: idempotent add, list, remove, dated
 * reminders, the alerts toggle, per-user isolation, and that a wishlisted
 * product can still be deleted. Runs the full HTTP → security → service → JPA
 * stack against H2 (the {@code test} profile).
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WishlistIntegrationTest {

    private static final String PASSWORD = "secret123";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private WishlistItemRepository wishlistItemRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private WishlistAlertService alertService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long productId;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() throws Exception {
        // Children first (FK order), so each test starts from a clean slate.
        notificationRepository.deleteAll();
        wishlistItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();

        Role customer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER")));
        saveUser("alice@example.com", customer);
        saveUser("bob@example.com", customer);

        Category category = categoryRepository.existsByName("Electronics")
                ? categoryRepository.findByName("Electronics")
                : categoryRepository.save(new Category("Electronics"));
        productId = productRepository.save(new Product(
                "Noise-cancelling Headphones", new BigDecimal("199.00"), "Over-ear", "Acme", 4, category)).getId();

        aliceToken = login("alice@example.com");
        bobToken = login("bob@example.com");
    }

    @Test
    @DisplayName("add is idempotent: 201 the first time, 200 after; the list shows the item")
    void add_isIdempotent_andListed() throws Exception {
        mockMvc.perform(put(itemUrl(productId)).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(itemUrl(productId))))
                .andExpect(jsonPath("$.data.product.id").value(productId))
                .andExpect(jsonPath("$.data.priceWhenAdded").value(199.00))
                .andExpect(jsonPath("$.data.alertsEnabled").value(true))
                .andExpect(jsonPath("$.data.remindAt").doesNotExist());

        mockMvc.perform(put(itemUrl(productId)).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Already in wishlist"));

        mockMvc.perform(get("/api/v1/wishlist").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].product.name").value("Noise-cancelling Headphones"))
                .andExpect(jsonPath("$.data[0].addedAt").isNotEmpty());
        assertThat(wishlistItemRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("adding a product that doesn't exist is a 404")
    void add_unknownProduct_isNotFound() throws Exception {
        mockMvc.perform(put(itemUrl(999_999L)).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("remove: 204, then 404 because it's no longer there")
    void remove_thenGone() throws Exception {
        add(aliceToken);

        mockMvc.perform(delete(itemUrl(productId)).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(itemUrl(productId)).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("reminder: a future time is stored, a past one is a 400, and it can be cleared")
    void reminder_setValidateAndClear() throws Exception {
        add(aliceToken);
        // @Future checks against the system clock, so use times that are always
        // in the future and always in the past rather than ones relative to now.
        Instant future = Instant.parse("2100-01-01T09:00:00Z");

        mockMvc.perform(put(itemUrl(productId) + "/reminder")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderBody(future)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remindAt").value(future.toString()));

        mockMvc.perform(put(itemUrl(productId) + "/reminder")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderBody(Instant.parse("2020-01-01T09:00:00Z"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.remindAt").value("Reminder time must be in the future"));

        mockMvc.perform(delete(itemUrl(productId) + "/reminder").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remindAt").doesNotExist());
    }

    @Test
    @DisplayName("alerts can be switched off and back on")
    void alerts_toggle() throws Exception {
        add(aliceToken);

        mockMvc.perform(put(itemUrl(productId) + "/alerts")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alertsEnabled").value(false));

        mockMvc.perform(put(itemUrl(productId) + "/alerts")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.enabled").exists());
    }

    @Test
    @DisplayName("switching alerts back on doesn't replay a price drop that happened while they were off")
    void reEnablingAlerts_startsFromCurrentPrice() throws Exception {
        add(aliceToken);
        setAlerts(aliceToken, false);
        Product product = productRepository.findById(productId).orElseThrow();
        product.setPrice(new BigDecimal("149.00"));
        productRepository.save(product);

        setAlerts(aliceToken, true);

        assertThat(alertService.announcePriceDrops(Instant.parse("2026-03-14T12:00:00Z"))).isZero();
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("each user sees and edits only their own wishlist")
    void wishlists_areIsolatedPerUser() throws Exception {
        add(aliceToken);

        mockMvc.perform(get("/api/v1/wishlist").header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        // Bob's wishlist doesn't contain it, so there is nothing of his to remove.
        mockMvc.perform(delete(itemUrl(productId)).header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound());
        assertThat(wishlistItemRepository.count()).as("Alice's item untouched").isEqualTo(1);
    }

    @Test
    @DisplayName("wishlist and inbox require a signed-in user")
    void endpoints_requireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/wishlist")).andExpect(status().isUnauthorized());
        mockMvc.perform(put(itemUrl(productId))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a wishlisted product can still be deleted; it drops out of the wishlist")
    void deletingProduct_removesItFromWishlists() throws Exception {
        add(aliceToken);
        add(bobToken);

        productRepository.deleteById(productId);

        assertThat(wishlistItemRepository.count()).isZero();
        mockMvc.perform(get("/api/v1/wishlist").header("Authorization", bearer(aliceToken)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // --- helpers -----------------------------------------------------------

    private void saveUser(String email, Role role) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRoles(Set.of(role));
        userRepository.save(user);
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("token").asText();
    }

    private void add(String token) throws Exception {
        mockMvc.perform(put(itemUrl(productId)).header("Authorization", bearer(token)))
                .andExpect(status().isCreated());
    }

    private void setAlerts(String token, boolean enabled) throws Exception {
        mockMvc.perform(put(itemUrl(productId) + "/alerts")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": %s}".formatted(enabled)))
                .andExpect(status().isOk());
    }

    private static String itemUrl(Long productId) {
        return "/api/v1/wishlist/items/" + productId;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String reminderBody(Instant at) {
        return "{\"remindAt\": \"%s\"}".formatted(at);
    }
}
