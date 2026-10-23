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
import com.aryan.spring_security_demo.payment.FakePaymentGateway;
import com.aryan.spring_security_demo.payment.Payment;
import com.aryan.spring_security_demo.payment.PaymentRepository;
import com.aryan.spring_security_demo.payment.PaymentStatus;
import com.aryan.spring_security_demo.payment.Refund;
import com.aryan.spring_security_demo.payment.RefundProcessor;
import com.aryan.spring_security_demo.payment.RefundRecorder;
import com.aryan.spring_security_demo.payment.RefundRepository;
import com.aryan.spring_security_demo.payment.RefundResult;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partial refunds (docs/payment-design.md, section 11) against MySQL, with the fake
 * provider: money and stock, so an integration test. Never more than was paid, even
 * with two admins at once; a cancellation refunds and restocks only what is left;
 * and only admins refund.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefundIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final BigDecimal UNIT_PRICE = new BigDecimal("19.99");
    private static final String ADDRESS_BODY = """
            {"recipientName":"Test Shopper","addressLine1":"1 Test St","addressLine2":"",
             "city":"Testville","state":"TS","postalCode":"12345","country":"Testland"}
            """;
    /** A refund after the commit runs on a background thread. */
    private static final Duration REFUND_WAIT = Duration.ofSeconds(10);
    private static final Instant FAR_FUTURE = Instant.parse("2100-01-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-18T09:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private RefundProcessor refundProcessor;
    @Autowired private RefundRecorder refundRecorder;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String passwordHash;
    private Long productId;
    private String shopperToken;
    private String otherToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        Role customer = role("ROLE_CUSTOMER");
        passwordHash = passwordEncoder.encode(PASSWORD);  // once: BCrypt is slow on purpose
        saveUser("refund-shopper@example.com", customer);
        saveUser("refund-other@example.com", customer);
        saveUser("refund-admin@example.com", role("ROLE_ADMIN"));
        productId = saveProduct();
        shopperToken = login("refund-shopper@example.com");
        otherToken = login("refund-other@example.com");
        adminToken = login("refund-admin@example.com");
    }

    // Other test classes delete orders, which a leftover payment would stop.
    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();  // and their refunds go with them
    }

    @Test
    @DisplayName("an admin refunds a returned item: priced at what was paid, restocked, and refunded by the provider")
    void aReturnedItem_isRefunded_atWhatWasPaid_andRestocked() throws Exception {
        Long orderId = paidOrder(2, FakePaymentGateway.VISA);
        Long itemId = itemIdOf(orderId);
        assertThat(inventoryOf(productId)).isEqualTo(98);

        refund(orderId, newKey(), itemsBody(itemId, 1, true))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.kind").value("ITEMS"))
                .andExpect(jsonPath("$.data.amount").value(19.99))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].orderItemId").value(itemId));

        awaitRefunds(orderId, Refund.Status.REFUNDED, 1);
        assertThat(inventoryOf(productId)).isEqualTo(99);
        assertThat(paymentOf(orderId).getStatus()).as("part of it kept").isEqualTo(PaymentStatus.SUCCEEDED);
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId).header("Authorization", bearer(shopperToken)))
                .andExpect(jsonPath("$.data.items[0].refundedQuantity").value(1));
        mockMvc.perform(get("/api/v1/orders/{orderId}/payments", orderId).header("Authorization", bearer(shopperToken)))
                .andExpect(jsonPath("$.data[0].refundedAmount").value(19.99));
        // The customer sees their refund, and its reason.
        mockMvc.perform(get("/api/v1/orders/{orderId}/refunds", orderId).header("Authorization", bearer(shopperToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reason").value("Returned: wrong size"))
                .andExpect(jsonPath("$.data[0].status").value("REFUNDED"));
    }

    @Test
    @DisplayName("partial refunds add up, and once every cent is back the payment is refunded")
    void partialRefunds_addUp_toAFullRefund() throws Exception {
        Long orderId = paidOrder(2, FakePaymentGateway.VISA);  // 39.98

        refund(orderId, newKey(), amountBody("10.00")).andExpect(status().isCreated());
        refund(orderId, newKey(), amountBody("29.98")).andExpect(status().isCreated());

        awaitRefunds(orderId, Refund.Status.REFUNDED, 2);
        await().atMost(REFUND_WAIT).until(() -> paymentOf(orderId).getStatus() == PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("more than is left to refund is a 409, and nothing is refunded")
    void moreThanIsLeft_is409() throws Exception {
        Long orderId = paidOrder(2, FakePaymentGateway.VISA);  // 39.98
        refund(orderId, newKey(), amountBody("30.00")).andExpect(status().isCreated());

        refund(orderId, newKey(), amountBody("10.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Only 9.98 USD is left")));

        assertThat(refundRepository.findAllByOrder(orderId)).hasSize(1);
        awaitRefunds(orderId, Refund.Status.REFUNDED, 1);
    }

    // The payment row is locked while a refund is added: the second waits, then sees the first.
    @Test
    @DisplayName("two admins refunding at once can't give back more than was paid")
    void twoRefundsAtOnce_neverAddUpToMoreThanWasPaid() throws Exception {
        Long orderId = paidOrder(2, FakePaymentGateway.VISA);  // 39.98

        List<Integer> statuses = allAtOnce(List.of(
                () -> refund(orderId, newKey(), amountBody("30.00")).andReturn().getResponse().getStatus(),
                () -> refund(orderId, newKey(), amountBody("30.00")).andReturn().getResponse().getStatus()));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        BigDecimal owed = refundRepository.findAllByOrder(orderId).stream()
                .map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(owed).isEqualByComparingTo("30.00");
        awaitRefunds(orderId, Refund.Status.REFUNDED, 1);
    }

    @Test
    @DisplayName("cancelling after an item was refunded refunds only the rest, and restocks only the rest")
    void cancelling_afterAPartialRefund_refundsAndRestocksOnlyTheRest() throws Exception {
        Long orderId = paidOrder(2, FakePaymentGateway.VISA);
        refund(orderId, newKey(), itemsBody(itemIdOf(orderId), 1, true)).andExpect(status().isCreated());
        awaitRefunds(orderId, Refund.Status.REFUNDED, 1);
        assertThat(inventoryOf(productId)).isEqualTo(99);

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId).header("Authorization", bearer(shopperToken)))
                .andExpect(status().isOk());

        await().atMost(REFUND_WAIT).until(() -> paymentOf(orderId).getStatus() == PaymentStatus.REFUNDED);
        assertThat(refundRepository.findAllByOrder(orderId))
                .extracting(Refund::getKind, refund -> refund.getAmount().toPlainString())
                .containsExactly(
                        tuple(Refund.Kind.ITEMS, "19.99"),
                        tuple(Refund.Kind.CANCELLATION, "19.99"));
        assertThat(inventoryOf(productId)).as("not restocked twice").isEqualTo(100);
    }

    // A cancellation reads its order before it locks the payment. Under REPEATABLE
    // READ its later plain reads would miss a refund committed after that first read.
    @Test
    @DisplayName("a cancellation that read the database before a partial refund committed still refunds only the rest")
    void aCancellation_thatReadBeforeAPartialRefund_refundsOnlyTheRest() throws Exception {
        Long orderId = paidOrder(2, FakePaymentGateway.VISA);  // 39.98

        List<Long> owed = afterAnEarlierRead(
                () -> refund(orderId, newKey(), amountBody("10.00")).andExpect(status().isCreated()),
                () -> refundRecorder.requestRefunds(List.of(orderId)));

        assertThat(refundRepository.findById(owed.get(0)).orElseThrow().getAmount()).isEqualByComparingTo("29.98");
        awaitRefunds(orderId, Refund.Status.REFUNDED, 1);  // the 10.00, before the next test clears the data
    }

    // A webhook has read its event before it locks the payment: the refunds it counts must still be the latest.
    @Test
    @DisplayName("recording the last refund refunds the payment, even in a transaction that read before the one before it was recorded")
    void recordingTheLastRefund_refundsThePayment_afterAnEarlierRead() throws Exception {
        Long orderId = paidOrder(2, FakePaymentGateway.VISA);  // 39.98
        Long paymentId = paymentOf(orderId).getId();
        // Owed without the event that has them sent, so this test records them itself.
        List<Long> owed = new TransactionTemplate(transactionManager).execute(status -> {
            Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
            return refundRepository.saveAll(List.of(
                    payment.refund(Refund.Kind.AMOUNT, new BigDecimal("10.00"), "Goodwill", null, "admin", NOW),
                    payment.refund(Refund.Kind.AMOUNT, new BigDecimal("29.98"), "Goodwill", null, "admin", NOW)))
                    .stream().map(Refund::getId).toList();
        });

        afterAnEarlierRead(
                () -> {
                    refundRecorder.recordRefunded(owed.get(0), new RefundResult("re_first"));
                    return null;
                },
                () -> {
                    refundRecorder.recordRefundedInCurrentTransaction(owed.get(1), new RefundResult("re_last"));
                    return null;
                });

        assertThat(paymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    // The outbox: owed in the request's transaction, retried by the job until the provider takes it.
    @Test
    @DisplayName("a partial refund the provider can't take yet stays owed, and the retry job refunds it")
    void aRefundTheProviderCantTakeYet_isRetriedByTheJob() throws Exception {
        Long orderId = paidOrder(1, FakePaymentGateway.REFUND_DOWN);
        refund(orderId, newKey(), amountBody("5.00")).andExpect(status().isCreated());
        // The attempt after the commit: this test card's provider refuses its first 3 calls.
        await().atMost(REFUND_WAIT).until(() -> refundRepository.findAllByOrder(orderId).get(0).getStatus()
                == Refund.Status.PENDING);

        await().atMost(REFUND_WAIT).until(() -> {
            refundProcessor.retryPendingRefunds(FAR_FUTURE);
            return refundRepository.findAllByOrder(orderId).get(0).getStatus() == Refund.Status.REFUNDED;
        });
    }

    @Test
    @DisplayName("the same Idempotency-Key again gets the first refund (200), and refunds nothing more")
    void aRepeatedRequest_getsTheFirstRefund() throws Exception {
        Long orderId = paidOrder(2, FakePaymentGateway.VISA);
        String key = newKey();
        long first = dataOf(refund(orderId, key, amountBody("5.00")).andExpect(status().isCreated()).andReturn())
                .path("id").asLong();

        refund(orderId, key, amountBody("5.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(first));

        assertThat(refundRepository.findAllByOrder(orderId)).hasSize(1);
        awaitRefunds(orderId, Refund.Status.REFUNDED, 1);
    }

    @Test
    @DisplayName("only an admin refunds (401, 403); an order's refunds are its owner's and admins' to see (403)")
    void onlyAdminsRefund_andOnlyTheOwnerOrAnAdminSeesRefunds() throws Exception {
        Long orderId = paidOrder(1, FakePaymentGateway.VISA);

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", orderId)
                        .header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON).content(amountBody("1.00")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", orderId)
                        .header("Authorization", bearer(shopperToken))
                        .header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON).content(amountBody("1.00")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/{orderId}/refunds", orderId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/{orderId}/refunds", orderId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        assertThat(refundRepository.findAllByOrder(orderId)).isEmpty();
    }

    @Test
    @DisplayName("an unpaid order can't be refunded (409), and an order that doesn't exist is 404")
    void anUnpaidOrder_is409_andAMissingOne404() throws Exception {
        Long unpaid = placeOrder(1);

        refund(unpaid, newKey(), amountBody("1.00")).andExpect(status().isConflict());
        refund(999_999L, newKey(), amountBody("1.00")).andExpect(status().isNotFound());
    }

    // --- helpers -----------------------------------------------------------

    private ResultActions refund(Long orderId, String key, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", orderId)
                .header("Authorization", bearer(adminToken))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String itemsBody(Long orderItemId, int quantity, boolean restock) {
        return """
                {"items": [{"orderItemId": %d, "quantity": %d}], "restock": %b, "reason": "Returned: wrong size"}
                """.formatted(orderItemId, quantity, restock);
    }

    private static String amountBody(String amount) {
        return "{\"amount\": " + amount + ", \"reason\": \"The box arrived damaged\"}";
    }

    /** An order of {@code quantity} of the product, paid with {@code method}. */
    private Long paidOrder(int quantity, String method) throws Exception {
        Long orderId = placeOrder(quantity);
        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                        .header("Authorization", bearer(shopperToken))
                        .header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"" + method + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        return orderId;
    }

    private Long itemIdOf(Long orderId) throws Exception {
        MvcResult order = mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("Authorization", bearer(shopperToken)))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(order).path("items").path(0).path("id").asLong();
    }

    /**
     * Runs {@code decide} in a transaction that read the database before
     * {@code meanwhile} committed, as a cancellation (its order) and a webhook (its
     * event) have read by the time they lock the payment.
     */
    private <T> T afterAnEarlierRead(Callable<?> meanwhile, Supplier<T> decide) throws Exception {
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch committed = new CountDownLatch(1);
        ExecutorService thread = Executors.newSingleThreadExecutor();
        try {
            Future<T> result = thread.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                productRepository.count();  // the transaction's first read
                read.countDown();
                await().atMost(Duration.ofSeconds(30)).until(() -> committed.getCount() == 0);
                return decide.get();
            }));
            assertThat(read.await(30, TimeUnit.SECONDS)).isTrue();
            meanwhile.call();
            committed.countDown();
            return result.get(30, TimeUnit.SECONDS);
        } finally {
            thread.shutdownNow();
        }
    }

    /**
     * Wait until {@code count} of the order's refunds are in {@code status}. A test
     * that has a refund sent waits for it to be recorded before it ends: recording
     * writes the payment, which the next test's clearData deletes.
     */
    private void awaitRefunds(Long orderId, Refund.Status status, int count) {
        await().atMost(REFUND_WAIT).until(() -> refundRepository.findAllByOrder(orderId).stream()
                .filter(refund -> refund.getStatus() == status).count() == count);
    }

    private Payment paymentOf(Long orderId) {
        return paymentRepository.findAllByOrder(orderId).get(0);
    }

    private void clearData() {
        paymentRepository.deleteAll();  // FK on orders, and their refunds go with them
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        refreshTokenRepository.deleteAll();  // FK on users — before the users
        userRepository.deleteAll();
        productRepository.deleteAll();
    }

    private Role role(String name) {
        return roleRepository.findByName(name).orElseGet(() -> roleRepository.save(new Role(name)));
    }

    private void saveUser(String email, Role role) {
        User user = new User();
        user.setFirstName("Rae");
        user.setLastName("Fund");
        user.setEmail(email);
        user.setPassword(passwordHash);
        user.setRoles(Set.of(role));
        userRepository.save(user);
    }

    /** A product at {@link #UNIT_PRICE} with 100 in stock. */
    private Long saveProduct() {
        Category category = categoryRepository.existsByName("Electronics")
                ? categoryRepository.findByName("Electronics")
                : categoryRepository.save(new Category("Electronics"));
        return productRepository.save(new Product("Card Reader", UNIT_PRICE, "", "Acme", 100, category)).getId();
    }

    private Long placeOrder(int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/cartItems")
                        .header("Authorization", bearer(shopperToken))
                        .param("productId", String.valueOf(productId))
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(shopperToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADDRESS_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        return dataOf(result).path("id").asLong();
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

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    /** Runs every call at the same moment, each on its own thread; returns the results in order. */
    private static <T> List<T> allAtOnce(List<Callable<T>> calls) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (Callable<T> call : calls) {
                futures.add(threads.submit(() -> {
                    start.await();
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
}
