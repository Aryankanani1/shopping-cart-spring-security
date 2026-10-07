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
import com.aryan.spring_security_demo.order.OrderRepository;
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

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What an admin's catalog changes do to customers' carts and checkout. Runs the
 * full HTTP → security → service → JPA stack against H2 ({@code test}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogChangeIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final String ADDRESS_BODY = """
            {"recipientName":"Test Shopper","addressLine1":"1 Test St",
             "city":"Testville","state":"TS","postalCode":"12345","country":"Testland"}
            """;

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
    @Autowired private PasswordEncoder passwordEncoder;

    private Long customerId;
    private Long productId;
    private String customer;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        refreshTokenRepository.deleteAll();  // FK on users — clear before the users
        userRepository.deleteAll();
        productRepository.deleteAll();

        customerId = saveUser("shopper@example.com", "ROLE_CUSTOMER");
        saveUser("admin@example.com", "ROLE_ADMIN");

        Category category = categoryRepository.existsByName("Electronics")
                ? categoryRepository.findByName("Electronics")
                : categoryRepository.save(new Category("Electronics"));
        productId = productRepository.save(new Product(
                "Desk Lamp", new BigDecimal("10.00"), "LED lamp", "Acme", 10, category)).getId();

        customer = "Bearer " + login("shopper@example.com");
        admin = "Bearer " + login("admin@example.com");
    }

    @Test
    @DisplayName("a price change reprices carts, and checkout charges the new price")
    void priceChange_repricesCartAndCheckout() throws Exception {
        addToCart(2);

        updateProduct(admin, "15.00", "Electronics").andExpect(status().isOk());

        Long cartId = cartRepository.findByUserId(customerId).getId();
        mockMvc.perform(get("/api/v1/carts/{id}", cartId).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartItems[0].unitPrice").value(15.00))
                .andExpect(jsonPath("$.data.totalAmount").value(30.00));

        mockMvc.perform(post("/api/v1/orders").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADDRESS_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(30.00));
    }

    @Test
    @DisplayName("a customer can't change a product's price")
    void priceChange_byCustomer_isForbidden() throws Exception {
        updateProduct(customer, "1.00", "Electronics").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updating a product without a category keeps the one it has")
    void update_withoutCategory_keepsCategory() throws Exception {
        updateProduct(admin, "10.00", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("Electronics"));
    }

    @Test
    @DisplayName("updating a product to a new category name creates it, as adding does")
    void update_withNewCategory_createsIt() throws Exception {
        updateProduct(admin, "10.00", "Lighting")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("Lighting"));
    }

    @Test
    @DisplayName("deleting a product that's in a cart removes it from the cart")
    void delete_productInCart_leavesTheCart() throws Exception {
        addToCart(2);

        mockMvc.perform(delete("/api/v1/products/{id}", productId).header("Authorization", admin))
                .andExpect(status().isNoContent());

        assertThat(productRepository.existsById(productId)).isFalse();
        Long cartId = cartRepository.findByUserId(customerId).getId();
        mockMvc.perform(get("/api/v1/carts/{id}", cartId).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartItems").isEmpty())
                .andExpect(jsonPath("$.data.totalAmount").value(0));
    }

    @Test
    @DisplayName("deleting a product that has been ordered is a 409 that says why")
    void delete_orderedProduct_isConflict() throws Exception {
        addToCart(1);
        mockMvc.perform(post("/api/v1/orders").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADDRESS_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/products/{id}", productId).header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Product in use"));

        assertThat(productRepository.existsById(productId)).isTrue();
    }

    @Test
    @DisplayName("a customer can't delete a product")
    void delete_byCustomer_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/products/{id}", productId).header("Authorization", customer))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private Long saveUser(String email, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRoles(Set.of(role));
        return userRepository.save(user).getId();
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

    private void addToCart(int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/cartItems").header("Authorization", customer)
                        .param("productId", String.valueOf(productId))
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isCreated());
    }

    private ResultActions updateProduct(String bearer, String price, String categoryName) throws Exception {
        String category = categoryName == null ? "" : """
                , "category": {"name": "%s"}""".formatted(categoryName);
        return mockMvc.perform(put("/api/v1/products/{id}", productId).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Desk Lamp", "brand": "Acme", "price": %s, "inventory": 10%s}
                        """.formatted(price, category)));
    }
}
