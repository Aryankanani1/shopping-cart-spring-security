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
import com.aryan.spring_security_demo.notification.Notification;
import com.aryan.spring_security_demo.notification.NotificationRepository;
import com.aryan.spring_security_demo.order.OrderRepository;
import com.aryan.spring_security_demo.wishlist.WishlistItem;
import com.aryan.spring_security_demo.wishlist.WishlistItemRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the in-app inbox: newest-first paging, the unread count
 * behind the header badge, marking read, dismissing, per-user isolation, and
 * entries outliving a deleted product. Notifications are seeded directly (the
 * scan that raises them is covered by {@code WishlistAlertServiceTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final Instant T0 = Instant.parse("2026-10-01T09:00:00Z");

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
    @Autowired private PasswordEncoder passwordEncoder;

    private Long productId;
    private Long reminderId;
    private Long bobNotificationId;
    private String aliceToken;

    @BeforeEach
    void setUp() throws Exception {
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
        User alice = saveUser("alice@example.com", customer);
        User bob = saveUser("bob@example.com", customer);

        Category category = categoryRepository.existsByName("Electronics")
                ? categoryRepository.findByName("Electronics")
                : categoryRepository.save(new Category("Electronics"));
        Product product = productRepository.save(new Product(
                "Desk Lamp", new BigDecimal("40.00"), "", "Acme", 3, category));
        productId = product.getId();

        WishlistItem aliceItem = wishlistItemRepository.save(new WishlistItem(alice, product, T0));
        WishlistItem bobItem = wishlistItemRepository.save(new WishlistItem(bob, product, T0));

        // Alice: three entries, oldest first. Bob: one.
        reminderId = notificationRepository.save(Notification.reminder(aliceItem, T0.plusSeconds(60))).getId();
        notificationRepository.save(Notification.priceDrop(
                aliceItem, new BigDecimal("40.00"), new BigDecimal("32.50"), T0.plusSeconds(120)));
        notificationRepository.save(Notification.backInStock(aliceItem, T0.plusSeconds(180)));
        bobNotificationId = notificationRepository.save(Notification.reminder(bobItem, T0.plusSeconds(60))).getId();

        aliceToken = login("alice@example.com");
    }

    @Test
    @DisplayName("the inbox lists only the caller's entries, newest first, paged")
    void list_newestFirst_paged() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].type").value("BACK_IN_STOCK"))
                .andExpect(jsonPath("$.data.content[1].type").value("PRICE_DROP"))
                .andExpect(jsonPath("$.data.content[1].oldPrice").value(40.00))
                .andExpect(jsonPath("$.data.content[1].newPrice").value(32.50))
                .andExpect(jsonPath("$.data.content[2].type").value("WISHLIST_REMINDER"))
                .andExpect(jsonPath("$.data.content[2].productId").value(productId))
                .andExpect(jsonPath("$.data.content[2].productName").value("Desk Lamp"))
                .andExpect(jsonPath("$.data.content[2].read").value(false));

        mockMvc.perform(get("/api/v1/notifications").param("size", "2").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    @DisplayName("marking one read, then all read, brings the unread count to zero")
    void unreadCount_markReadAndReadAll() throws Exception {
        unreadCountIs(3);

        mockMvc.perform(post("/api/v1/notifications/" + reminderId + "/read").header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        unreadCountIs(2);

        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        unreadCountIs(0);
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.content[0].read").value(true));
    }

    @Test
    @DisplayName("dismissing an entry removes it; dismissing it again is a 404")
    void delete_removesEntry() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/" + reminderId).header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/notifications/" + reminderId).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
        unreadCountIs(2);
    }

    @Test
    @DisplayName("another user's entry looks like a missing one (404) and is left untouched")
    void othersEntries_areNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/" + bobNotificationId + "/read").header("Authorization", bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/notifications/" + bobNotificationId).header("Authorization", bearer()))
                .andExpect(status().isNotFound());

        Notification bobs = notificationRepository.findById(bobNotificationId).orElseThrow();
        assertThat(bobs.getReadAt()).isNull();
    }

    @Test
    @DisplayName("entries outlive a deleted product: the link is dropped, the name is kept")
    void deletedProduct_keepsEntryWithoutLink() throws Exception {
        productRepository.deleteById(productId);

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].productId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].productName").value("Desk Lamp"));
    }

    // --- helpers -----------------------------------------------------------

    private void unreadCountIs(long expected) throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(expected));
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRoles(Set.of(role));
        return userRepository.save(user);
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

    private String bearer() {
        return "Bearer " + aliceToken;
    }
}
