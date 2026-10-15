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
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutJourneyIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final BigDecimal UNIT_PRICE = new BigDecimal("19.99");
    private static final int INITIAL_INVENTORY = 10;
    private static final int ORDER_QUANTITY = 2;
    private static final String ADDRESS_BODY = """
            {"recipientName":"Test Shopper","addressLine1":"1 Test St","addressLine2":"",
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

    private Long userId;
    private Long productId;
    private Role customer;
    private String passwordHash;

    @BeforeEach
    void setUp() {
        // Clean slate (children first) so each test commits against a known state.
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        refreshTokenRepository.deleteAll();  // FK on users — must go before the users
        userRepository.deleteAll();
        productRepository.deleteAll();

        customer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER")));
        passwordHash = passwordEncoder.encode(PASSWORD);  // once: BCrypt is slow on purpose
        userId = saveShopper("shopper@example.com");
        productId = saveProduct("Wireless Mouse");
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

        // 3) CHECKOUT — place the order for this user, with a shipping address.
        MvcResult orderResult = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADDRESS_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Item Order Success!"))
                .andReturn();

        JsonNode order = dataOf(orderResult);
        assertThat(order.path("status").asText()).isEqualTo("PENDING");
        assertThat(order.path("city").asText()).isEqualTo("Testville");
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
    @DisplayName("GET cart serializes its lazy items without a LazyInitializationException")
    void getCart_serializesLazyItems() throws Exception {
        String token = login("shopper@example.com", PASSWORD);

        mockMvc.perform(post("/api/v1/cartItems")
                        .header("Authorization", "Bearer " + token)
                        .param("productId", String.valueOf(productId))
                        .param("quantity", String.valueOf(ORDER_QUANTITY)))
                .andExpect(status().isCreated());

        Long cartId = cartRepository.findByUserId(userId).getId();

        // With open-in-view off, this used to throw because the controller
        // serialized the Cart entity's lazy cartItems after the tx had closed.
        // The service now maps to a DTO inside the transaction.
        mockMvc.perform(get("/api/v1/carts/{cartId}", cartId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartId").value(cartId))
                .andExpect(jsonPath("$.data.cartItems", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.cartItems[0].quantity").value(ORDER_QUANTITY))
                .andExpect(jsonPath("$.data.cartItems[0].product.name").value("Wireless Mouse"));
    }

    @Test
    @DisplayName("GET /carts/mine serves the caller's cart with its nested lazy graph")
    void myCart_withItems_serializesLazyGraph() throws Exception {
        String token = login("shopper@example.com", PASSWORD);
        addToCart(token, ORDER_QUANTITY).andExpect(status().isCreated());

        // The service loads and maps the cart in one read-only transaction, so the
        // lazy cartItems -> product -> images graph is readable (open-in-view is off).
        mockMvc.perform(get("/api/v1/carts/mine").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartId").value(cartRepository.findByUserId(userId).getId()))
                .andExpect(jsonPath("$.data.cartItems", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.cartItems[0].quantity").value(ORDER_QUANTITY))
                .andExpect(jsonPath("$.data.cartItems[0].product.name").value("Wireless Mouse"));
    }

    @Test
    @DisplayName("GET /carts/mine is 404 before the first add-to-cart, and 401 without a token")
    void myCart_noCartYet_is404_andAnonymous_is401() throws Exception {
        String token = login("shopper@example.com", PASSWORD);

        mockMvc.perform(get("/api/v1/carts/mine").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/carts/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT quantity addresses the cart line by its item id and updates the cart")
    void updateQuantity_byItemId_updatesLineAndTotal() throws Exception {
        String token = login("shopper@example.com", PASSWORD);
        addToCart(token, 1).andExpect(status().isCreated());

        Long cartId = cartRepository.findByUserId(userId).getId();
        Long itemId = cartItemRepository.findAll().get(0).getId();

        mockMvc.perform(put("/api/v1/cartItems/cart/{cartId}/item/{itemId}", cartId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .param("quantity", "3"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/carts/{cartId}", cartId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartItems[0].quantity").value(3))
                .andExpect(jsonPath("$.data.totalAmount").value(59.97));
    }

    // Regression: simultaneous adds to one cart raced on the cart row. On MySQL
    // they deadlocked (500) or failed the cart's version check (409).
    @Test
    @DisplayName("simultaneous adds to one cart all succeed, and every unit lands")
    void addToCart_simultaneously_everyAddLands() throws Exception {
        String token = login("shopper@example.com", PASSWORD);
        addToCart(token, 1).andExpect(status().isCreated());  // the cart now exists
        Long cartId = cartRepository.findByUserId(userId).getId();

        int requests = 8;
        List<Callable<MvcResult>> adds = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            adds.add(() -> addToCart(token, 1).andReturn());
        }

        assertThat(statusesOf(allAtOnce(adds))).as("status of each simultaneous add").containsOnly(201);
        mockMvc.perform(get("/api/v1/carts/{cartId}", cartId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartItems.length()").value(1))
                .andExpect(jsonPath("$.data.cartItems[0].quantity").value(requests + 1))
                .andExpect(jsonPath("$.data.totalAmount").value(179.91));  // 9 x 19.99
    }

    @Test
    @DisplayName("adding more than is in stock returns 409 and adds nothing")
    void addToCart_beyondStock_isConflict() throws Exception {
        String token = login("shopper@example.com", PASSWORD);

        addToCart(token, INITIAL_INVENTORY + 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient stock"))
                .andExpect(jsonPath("$.detail").value("Only 10 of Wireless Mouse left in stock"));

        assertThat(cartItemRepository.count()).isZero();
    }

    @Test
    @DisplayName("a quantity below 1 is a 400 validation error")
    void addToCart_zeroQuantity_isBadRequest() throws Exception {
        String token = login("shopper@example.com", PASSWORD);

        addToCart(token, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    @DisplayName("checkout with no cart returns 409, not a 500")
    void checkout_withNoCart_isConflict() throws Exception {
        String token = login("shopper@example.com", PASSWORD);

        placeOrder(token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Cart is empty"));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("checkout after stock dropped below the cart quantity returns 409 and changes nothing")
    void checkout_whenStockDroppedBelowCart_isConflict() throws Exception {
        String token = login("shopper@example.com", PASSWORD);
        addToCart(token, ORDER_QUANTITY).andExpect(status().isCreated());

        // Someone else bought most of the stock after this shopper filled the cart.
        Product product = productRepository.findById(productId).orElseThrow();
        product.setInventory(1);
        productRepository.save(product);

        placeOrder(token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient stock"));

        assertThat(orderRepository.count()).isZero();
        assertThat(productRepository.findById(productId).orElseThrow().getInventory()).isEqualTo(1);
        assertThat(cartItemRepository.count()).as("cart kept so the shopper can adjust it").isEqualTo(1);
    }

    // Regression: checkouts that share a product all read the same stock and version.
    // The first to commit won, and every other one failed Product's version check
    // with a 409 "Concurrent modification", however much stock was left.
    @Test
    @DisplayName("simultaneous checkouts of the same products all succeed, and the stock adds up")
    void checkout_simultaneouslyByManyShoppers_allSucceed() throws Exception {
        Long hubId = saveProduct("USB Hub");
        int shoppers = 6;
        List<Callable<MvcResult>> checkouts = new ArrayList<>();
        for (int i = 0; i < shoppers; i++) {
            String email = "racer" + i + "@example.com";
            saveShopper(email);
            String token = login(email, PASSWORD);
            // Half the carts list the two products the other way round: checkouts
            // that lock shared rows in opposite orders can deadlock.
            for (Long product : i % 2 == 0 ? List.of(productId, hubId) : List.of(hubId, productId)) {
                addToCart(token, product, 1).andExpect(status().isCreated());
            }
            checkouts.add(() -> placeOrder(token).andReturn());
        }

        List<MvcResult> results = allAtOnce(checkouts);

        assertThat(statusesOf(results)).as("status of each checkout").containsOnly(201);
        assertThat(orderRepository.count()).isEqualTo(shoppers);
        assertThat(inventoryOf(productId)).isEqualTo(INITIAL_INVENTORY - shoppers);
        assertThat(inventoryOf(hubId)).isEqualTo(INITIAL_INVENTORY - shoppers);
    }

    @Test
    @DisplayName("shoppers racing for the last unit: one buys it, the others are told it's out of stock")
    void checkout_simultaneouslyForTheLastUnit_sellsItOnce() throws Exception {
        int shoppers = 4;
        List<Callable<MvcResult>> checkouts = new ArrayList<>();
        for (int i = 0; i < shoppers; i++) {
            String email = "racer" + i + "@example.com";
            saveShopper(email);
            String token = login(email, PASSWORD);
            addToCart(token, 1).andExpect(status().isCreated());
            checkouts.add(() -> placeOrder(token).andReturn());
        }
        Product product = productRepository.findById(productId).orElseThrow();
        product.setInventory(1);  // all but one unit sold since they filled their carts
        productRepository.save(product);

        List<MvcResult> results = allAtOnce(checkouts);

        assertThat(statusesOf(results)).as("status of each checkout")
                .containsOnlyOnce(201)
                .containsOnly(201, 409);
        for (MvcResult result : results) {
            if (result.getResponse().getStatus() == 409) {
                assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("title").asText())
                        .as("why a losing checkout was refused")
                        .isEqualTo("Insufficient stock");
            }
        }
        assertThat(inventoryOf(productId)).isZero();
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(cartItemRepository.count()).as("the others keep their carts").isEqualTo(shoppers - 1);
    }

    // Regression: a cancellation read its products without a lock, so a checkout of
    // the same product committing meanwhile failed the cancellation's version check:
    // a 409, and the customer had to cancel again.
    @Test
    @DisplayName("a cancellation during checkouts of the same product succeeds, and the stock adds up")
    void cancel_duringCheckoutsOfTheSameProduct_succeeds() throws Exception {
        String canceller = login("shopper@example.com", PASSWORD);
        addToCart(canceller, ORDER_QUANTITY).andExpect(status().isCreated());
        long orderId = dataOf(placeOrder(canceller).andExpect(status().isCreated()).andReturn()).path("id").asLong();

        int shoppers = 5;
        List<Callable<MvcResult>> requests = new ArrayList<>();
        requests.add(() -> mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                .header("Authorization", "Bearer " + canceller)).andReturn());
        for (int i = 0; i < shoppers; i++) {
            String email = "racer" + i + "@example.com";
            saveShopper(email);
            String token = login(email, PASSWORD);
            addToCart(token, 1).andExpect(status().isCreated());
            requests.add(() -> placeOrder(token).andReturn());
        }

        List<MvcResult> results = allAtOnce(requests);

        assertThat(statusesOf(results)).as("the cancellation, then each checkout")
                .containsExactly(200, 201, 201, 201, 201, 201);
        assertThat(inventoryOf(productId)).as("the cancelled units are back, the bought ones gone")
                .isEqualTo(INITIAL_INVENTORY - shoppers);
    }

    @Test
    @DisplayName("adding to the cart without a token is rejected (guard holds)")
    void addToCart_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/cartItems")
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "1"))
                .andExpect(status().isUnauthorized())
                // The entry point renders the same RFC 7807 shape as every other error.
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401));

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
    @DisplayName("registration: a password over BCrypt's 72 bytes is a 400, not a 500")
    void register_passwordOver72Bytes_returns400() throws Exception {
        String body = """
                {
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "email": "long-password@example.com",
                  "password": "%s"
                }
                """.formatted("é".repeat(40));  // 40 characters, 80 bytes

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
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

    @Test
    @DisplayName("GET product maps to a DTO in-tx: no LazyInitializationException on category/images")
    void getProductById_serializesDto() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Wireless Mouse"))
                .andExpect(jsonPath("$.data.categoryName").value("Electronics"))
                .andExpect(jsonPath("$.data.images").isArray());
    }

    @Test
    @DisplayName("GET categories returns DTOs, not the entity (no version leaked)")
    void getCategories_returnDto() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", hasItem("Electronics")))
                .andExpect(jsonPath("$.data[0].version").doesNotExist());
    }

    // --- helpers -----------------------------------------------------------

    /** A customer with {@link #PASSWORD}; returns their id. */
    private Long saveShopper(String email) {
        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setEmail(email);
        user.setPassword(passwordHash);
        user.setRoles(Set.of(customer));
        return userRepository.save(user).getId();
    }

    /** A product at {@link #UNIT_PRICE} with {@link #INITIAL_INVENTORY} in stock; returns its id. */
    private Long saveProduct(String name) {
        Category category = categoryRepository.existsByName("Electronics")
                ? categoryRepository.findByName("Electronics")
                : categoryRepository.save(new Category("Electronics"));
        return productRepository.save(
                new Product(name, UNIT_PRICE, "", "Acme", INITIAL_INVENTORY, category)).getId();
    }

    private int inventoryOf(Long id) {
        return productRepository.findById(id).orElseThrow().getInventory();
    }

    /** Runs every call at the same moment, each on its own thread; returns the results in order. */
    private static <T> List<T> allAtOnce(List<Callable<T>> calls) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (Callable<T> call : calls) {
                futures.add(threads.submit(() -> {
                    start.await();  // release every call at once
                    return call.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            threads.shutdownNow();
        }
    }

    private static List<Integer> statusesOf(List<MvcResult> results) {
        return results.stream().map(result -> result.getResponse().getStatus()).toList();
    }

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

    private ResultActions addToCart(String token, int quantity) throws Exception {
        return addToCart(token, productId, quantity);
    }

    private ResultActions addToCart(String token, Long product, int quantity) throws Exception {
        return mockMvc.perform(post("/api/v1/cartItems")
                .header("Authorization", "Bearer " + token)
                .param("productId", String.valueOf(product))
                .param("quantity", String.valueOf(quantity)));
    }

    private ResultActions placeOrder(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ADDRESS_BODY));
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
