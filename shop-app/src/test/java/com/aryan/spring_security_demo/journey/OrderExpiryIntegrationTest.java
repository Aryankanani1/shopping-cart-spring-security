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
import com.aryan.spring_security_demo.order.Order;
import com.aryan.spring_security_demo.order.OrderRepository;
import com.aryan.spring_security_demo.order.OrderStatus;
import com.aryan.spring_security_demo.order.UnpaidOrderExpiryJob;
import com.aryan.spring_security_demo.payment.FakePaymentGateway;
import com.aryan.spring_security_demo.payment.Payment;
import com.aryan.spring_security_demo.payment.PaymentRepository;
import com.aryan.spring_security_demo.payment.PaymentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unpaid orders expire, through the real job method against MySQL: checkout
 * reserved their stock, so an order left unpaid is cancelled and its items go back
 * on sale. The job is called directly with a cutoff the test picks (its cron is
 * off in the test profile): a far-future cutoff makes every unpaid order old.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderExpiryIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final int STOCK = 100;
    private static final Instant FAR_FUTURE = Instant.parse("2100-01-01T00:00:00Z");
    private static final Instant LONG_AGO = Instant.parse("2000-01-01T00:00:00Z");
    private static final String ADDRESS_BODY = """
            {"recipientName":"Test Shopper","addressLine1":"1 Test St","addressLine2":"",
             "city":"Testville","state":"TS","postalCode":"12345","country":"Testland"}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private UnpaidOrderExpiryJob expiryJob;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long productId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        Role customer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER")));
        User user = new User();
        user.setFirstName("Una");
        user.setLastName("Paid");
        user.setEmail("unpaid@example.com");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRoles(Set.of(customer));
        userRepository.save(user);
        Category category = categoryRepository.existsByName("Electronics")
                ? categoryRepository.findByName("Electronics")
                : categoryRepository.save(new Category("Electronics"));
        productId = productRepository.save(
                new Product("Desk Fan", new BigDecimal("24.00"), "", "Acme", STOCK, category)).getId();
        token = login("unpaid@example.com");
    }

    // Other test classes delete orders, which a leftover payment would stop.
    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("an unpaid order says when it must be paid by, and is cancelled and restocked once that has passed")
    void anOldUnpaidOrder_isCancelled_andItsStockReturned() throws Exception {
        Long orderId = placeOrder(3);
        Order placed = orderRepository.findById(orderId).orElseThrow();
        MvcResult shown = mockMvc.perform(get("/api/v1/orders/{orderId}", orderId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        // 30 minutes after it was placed (the database keeps microseconds, the API the same).
        assertThat(Instant.parse(dataOf(shown).path("payBy").asText()))
                .isEqualTo(placed.getCreatedAt().plus(Duration.ofMinutes(30)));
        assertThat(inventoryOf(productId)).isEqualTo(STOCK - 3);

        assertThat(expiryJob.expireUnpaidOrders(FAR_FUTURE)).isEqualTo(1);

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(inventoryOf(productId)).isEqualTo(STOCK);
        // Too late to pay for it now.
        pay(orderId, FakePaymentGateway.VISA).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("orders placed after the cutoff, and paid ones, are left alone")
    void freshAndPaidOrders_areLeftAlone() throws Exception {
        Long fresh = placeOrder(1);
        Long paid = placeOrder(1);
        pay(paid, FakePaymentGateway.VISA).andExpect(status().isCreated());

        assertThat(expiryJob.expireUnpaidOrders(LONG_AGO)).isZero();
        assertThat(orderStatus(fresh)).isEqualTo(OrderStatus.PENDING);

        assertThat(expiryJob.expireUnpaidOrders(FAR_FUTURE)).isEqualTo(1);  // only the unpaid one
        assertThat(orderStatus(paid)).isEqualTo(OrderStatus.PAID);
        assertThat(orderStatus(fresh)).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("an order whose payment is in progress isn't expired, and doesn't hold back the others")
    void anOrderBeingPaidFor_isSkipped_andTheRestExpire() throws Exception {
        Long beingPaid = placeOrder(1);
        Long abandoned = placeOrder(1);
        // A payment the provider hasn't answered yet: it may still be charged.
        Order order = orderRepository.findById(beingPaid).orElseThrow();
        paymentRepository.save(Payment.start(order, UUID.randomUUID().toString(), FakePaymentGateway.VISA, "USD",
                Instant.parse("2026-10-16T12:00:00Z")));

        assertThat(expiryJob.expireUnpaidOrders(FAR_FUTURE)).isEqualTo(1);

        assertThat(orderStatus(beingPaid)).isEqualTo(OrderStatus.PENDING);
        assertThat(orderStatus(abandoned)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(inventoryOf(productId)).isEqualTo(STOCK - 1);  // only the abandoned one's back
    }

    @Test
    @DisplayName("paying and expiring at once never leave a cancelled order charged")
    void payAndExpireAtOnce_neverLeaveACancelledOrderCharged() throws Exception {
        for (int round = 0; round < 3; round++) {
            Long orderId = placeOrder(1);

            List<Object> results = allAtOnce(List.of(
                    () -> pay(orderId, FakePaymentGateway.VISA).andReturn().getResponse().getStatus(),
                    () -> expiryJob.expireUnpaidOrders(FAR_FUTURE)));

            OrderStatus status = orderStatus(orderId);
            List<PaymentStatus> payments = paymentRepository.findAllByOrder(orderId).stream()
                    .map(Payment::getStatus).toList();
            if (results.get(0).equals(201)) {
                assertThat(status).as("round %d", round).isEqualTo(OrderStatus.PAID);
                assertThat(payments).containsExactly(PaymentStatus.SUCCEEDED);
            } else {
                assertThat(results.get(0)).as("round %d", round).isEqualTo(409);
                assertThat(status).isEqualTo(OrderStatus.CANCELLED);
                assertThat(payments).isEmpty();
            }
        }
    }

    // --- helpers -----------------------------------------------------------

    private void clearData() {
        paymentRepository.deleteAll();  // FK on orders — before the orders
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        refreshTokenRepository.deleteAll();  // FK on users — before the users
        userRepository.deleteAll();
        productRepository.deleteAll();
    }

    /** Check out {@code quantity} of the product; returns the new order's id. */
    private Long placeOrder(int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/cartItems")
                        .header("Authorization", bearer(token))
                        .param("productId", String.valueOf(productId))
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADDRESS_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        return dataOf(result).path("id").asLong();
    }

    private ResultActions pay(Long orderId, String paymentMethod) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\":\"" + paymentMethod + "\"}"));
    }

    private OrderStatus orderStatus(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getOrderStatus();
    }

    private int inventoryOf(Long id) {
        return productRepository.findById(id).orElseThrow().getInventory();
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result).path("token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Runs every call at the same moment, each on its own thread; returns the results in order. */
    private static List<Object> allAtOnce(List<Callable<Object>> calls) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (Callable<Object> call : calls) {
                futures.add(threads.submit(() -> {
                    start.await();  // release every call at once
                    return call.call();
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            threads.shutdownNow();
        }
    }

    /** The {@code data} node of an {@code ApiResponse} JSON body. */
    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
