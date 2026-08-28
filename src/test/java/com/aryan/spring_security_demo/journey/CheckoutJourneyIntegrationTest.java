package com.aryan.spring_security_demo.journey;

import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.model.Role;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.repository.CartItemRepository;
import com.aryan.spring_security_demo.repository.CartRepository;
import com.aryan.spring_security_demo.repository.CategoryRepository;
import com.aryan.spring_security_demo.repository.OrderRepository;
import com.aryan.spring_security_demo.repository.ProductRepository;
import com.aryan.spring_security_demo.repository.RoleRepository;
import com.aryan.spring_security_demo.repository.UserRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end journey: <b>log in → add an item to the cart → check out</b>.
 *
 * <p>This exercises the whole stack for real — HTTP, the JWT security filter,
 * controllers, services and JPA against an in-memory H2 database (the {@code test}
 * profile). The point is to catch anything that breaks <em>in between</em> the
 * steps: the auth guard, the token round-trip, cart totals, order creation,
 * inventory decrement and cart clean-up. Nothing is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutJourneyIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final BigDecimal UNIT_PRICE = new BigDecimal("19.99");
    private static final int INITIAL_INVENTORY = 10;
    private static final int ORDER_QUANTITY = 2;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private MockMvc mockMvc;

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        // Clean slate (children first) so each test commits against a known state.
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();

        Role customer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER")));

        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setEmail("shopper@example.com");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRoles(Set.of(customer));
        userId = userRepository.save(user).getId();

        Category category = categoryRepository.existsByName("Electronics")
                ? categoryRepository.findByName("Electronics")
                : categoryRepository.save(new Category("Electronics"));

        Product product = new Product(
                "Wireless Mouse", UNIT_PRICE, "Ergonomic mouse", "Acme",
                INITIAL_INVENTORY, category);
        productId = productRepository.save(product).getId();
    }

    @Test
    @DisplayName("login → add to cart → checkout: state is correct at every step")
    void fullJourney_login_addToCart_checkout() throws Exception {
        // 1) LOGIN — get a JWT for the shopper.
        String token = login("shopper@example.com", PASSWORD);

        // 2) ADD TO CART — authenticated; a cart is created on the fly.
        mockMvc.perform(post("/api/v1/cartItems")
                        .header("Authorization", "Bearer " + token)
                        .param("productId", String.valueOf(productId))
                        .param("quantity", String.valueOf(ORDER_QUANTITY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("add item successfully!"));

        // --- IN BETWEEN: the cart now exists for this user with the right line ---
        var cart = cartRepository.findByUserId(userId);
        assertThat(cart).as("cart created for the shopper").isNotNull();
        Long cartId = cart.getId();

        BigDecimal expectedTotal = UNIT_PRICE.multiply(BigDecimal.valueOf(ORDER_QUANTITY));

        MvcResult totalResult = mockMvc.perform(get("/api/v1/carts/{cartId}/total-price", cartId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        BigDecimal cartTotal = dataOf(totalResult).decimalValue();
        assertThat(cartTotal)
                .as("cart total = unit price * quantity")
                .isEqualByComparingTo(expectedTotal);

        // 3) CHECKOUT — place the order for this user.
        MvcResult orderResult = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .param("userId", String.valueOf(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Item Order Success!"))
                .andReturn();

        JsonNode order = dataOf(orderResult);
        assertThat(order.path("status").asText()).isEqualTo("PENDING");
        assertThat(order.path("userId").asLong()).isEqualTo(userId);
        assertThat(order.path("items")).hasSize(1);
        assertThat(order.path("totalAmount").decimalValue())
                .as("order total matches the cart")
                .isEqualByComparingTo(expectedTotal);

        // --- IN BETWEEN: checkout must decrement inventory and clear the cart ---
        Product afterCheckout = productRepository.findById(productId).orElseThrow();
        assertThat(afterCheckout.getInventory())
                .as("inventory reduced by the ordered quantity")
                .isEqualTo(INITIAL_INVENTORY - ORDER_QUANTITY);

        assertThat(cartRepository.findByUserId(userId))
                .as("cart cleared after checkout")
                .isNull();
    }

    @Test
    @DisplayName("adding to the cart without a token is rejected (guard holds)")
    void addToCart_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/cartItems")
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "1"))
                .andExpect(status().isUnauthorized());

        assertThat(cartRepository.findByUserId(userId))
                .as("no cart is created for an unauthenticated request")
                .isNull();
    }

    @Test
    @DisplayName("login with a wrong password returns 401 and no token")
    void login_withWrongPassword_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("shopper@example.com", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("global handler: a missing resource returns RFC 7807 404")
    void missingResource_returnsProblemDetail404() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", productId + 999_999))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("global handler: creating a duplicate user returns RFC 7807 409")
    void duplicateUser_returnsProblemDetail409() throws Exception {
        String body = """
                {
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "email": "shopper@example.com",
                  "password": "secret123"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource already exists"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("framework 4xx: malformed JSON body stays a 400, not a 500")
    void malformedJson_returnsProblemDetail400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // --- helpers -----------------------------------------------------------

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        return dataOf(result).path("token").asText();
    }

    private String loginBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginPayload(email, password));
    }

    /** The {@code data} node of an {@code ApiResponse} JSON body. */
    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private record LoginPayload(String email, String password) {}
}
