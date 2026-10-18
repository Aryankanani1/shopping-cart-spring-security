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
import com.aryan.spring_security_demo.order.OrderStatus;
import com.aryan.spring_security_demo.payment.ChargeRequest;
import com.aryan.spring_security_demo.payment.ChargeResult;
import com.aryan.spring_security_demo.payment.FakePaymentGateway;
import com.aryan.spring_security_demo.payment.Payment;
import com.aryan.spring_security_demo.payment.PaymentReconciler;
import com.aryan.spring_security_demo.payment.PaymentRepository;
import com.aryan.spring_security_demo.payment.RefundRepository;
import com.aryan.spring_security_demo.payment.PaymentStatus;
import com.aryan.spring_security_demo.payment.PaymentWebhookEventRepository;
import com.aryan.spring_security_demo.payment.WebhookSignature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The provider's webhooks, through HTTP and against MySQL: only a correctly
 * signed, recent request is accepted, with no user token; an event changes things
 * once however often it is delivered, even twice at the same moment; and a webhook
 * settles payments and refunds through the same transitions as everything else.
 *
 * <p>The test profile sets a webhook secret but no delivery URL, so the fake
 * provider sends nothing itself: each test signs and posts the events it needs.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentWebhookIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final String WEBHOOKS = "/api/v1/payments/webhooks";
    private static final String ADDRESS_BODY = """
            {"recipientName":"Test Shopper","addressLine1":"1 Test St","addressLine2":"",
             "city":"Testville","state":"TS","postalCode":"12345","country":"Testland"}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.payment.webhook-secret}") private String secret;
    @Autowired private MockMvc mockMvc;
    @Autowired private FakePaymentGateway gateway;
    @Autowired private PaymentReconciler reconciler;
    @Autowired private PaymentWebhookEventRepository webhookEvents;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;
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
        user.setFirstName("Hook");
        user.setLastName("Tester");
        user.setEmail("webhooks@example.com");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRoles(Set.of(customer));
        userRepository.save(user);
        Category category = categoryRepository.existsByName("Electronics")
                ? categoryRepository.findByName("Electronics")
                : categoryRepository.save(new Category("Electronics"));
        productId = productRepository.save(
                new Product("Hub", new BigDecimal("15.00"), "", "Acme", 100, category)).getId();
        token = login();
    }

    // Other test classes delete orders, which a leftover payment would stop.
    @AfterEach
    void tearDown() {
        webhookEvents.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("a payment the provider timed out on is settled by its webhook, with no user token")
    void aTimedOutPayment_isSettledByItsWebhook() throws Exception {
        Long orderId = placeOrder();
        pay(orderId, FakePaymentGateway.TIMEOUT_CHARGED).andExpect(status().isAccepted());
        Long paymentId = paymentOf(orderId).getId();

        deliver(chargeSucceeded("evt_settle", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Webhook received"));

        Payment settled = paymentOf(orderId);
        assertThat(settled.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(settled.getCardLast4()).isEqualTo("4242");
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("an event delivered again changes nothing, and is answered 200")
    void aRepeatedEvent_changesNothing() throws Exception {
        Long orderId = placeOrder();
        pay(orderId, FakePaymentGateway.TIMEOUT_CHARGED).andExpect(status().isAccepted());
        String event = chargeSucceeded("evt_twice", paymentOf(orderId).getId());

        deliver(event).andExpect(status().isOk());
        long version = paymentOf(orderId).getVersion();
        deliver(event).andExpect(status().isOk());

        assertThat(webhookEvents.count()).isEqualTo(1);
        assertThat(paymentOf(orderId).getVersion()).isEqualTo(version);
    }

    @Test
    @DisplayName("the same event delivered twice at the same moment is processed once")
    void aDuplicateAtTheSameMoment_isProcessedOnce() throws Exception {
        for (int round = 0; round < 3; round++) {
            Long orderId = placeOrder();
            pay(orderId, FakePaymentGateway.TIMEOUT_CHARGED).andExpect(status().isAccepted());
            String event = chargeSucceeded("evt_race_" + round, paymentOf(orderId).getId());

            List<Object> statuses = allAtOnce(List.of(
                    () -> deliver(event).andReturn().getResponse().getStatus(),
                    () -> deliver(event).andReturn().getResponse().getStatus()));

            assertThat(statuses).as("round %d", round).containsExactly(200, 200);
            assertThat(paymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        }
        assertThat(webhookEvents.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("a webhook and reconciliation settling one payment at once both succeed; the first records it")
    void aWebhookAndReconciliationAtOnce_takeTurns() throws Exception {
        for (int round = 0; round < 3; round++) {
            Long orderId = placeOrder();
            pay(orderId, FakePaymentGateway.TIMEOUT_CHARGED).andExpect(status().isAccepted());
            Long paymentId = paymentOf(orderId).getId();
            String event = chargeSucceeded("evt_vs_reconcile_" + round, paymentId);

            List<Object> results = allAtOnce(List.of(
                    () -> deliver(event).andReturn().getResponse().getStatus(),
                    () -> reconciler.reconcile(paymentId)));

            // Without the row lock, the second to commit failed its version check.
            assertThat(results).as("round %d", round).containsExactly(200, true);
            assertThat(paymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID);
        }
    }

    @Test
    @DisplayName("an unsigned, badly signed or stale webhook is refused with 401 and changes nothing")
    void anUnverifiableWebhook_isRefused() throws Exception {
        Long orderId = placeOrder();
        pay(orderId, FakePaymentGateway.TIMEOUT_CHARGED).andExpect(status().isAccepted());
        byte[] body = chargeSucceeded("evt_forged", paymentOf(orderId).getId()).getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post(WEBHOOKS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        deliver(body, WebhookSignature.sign("forged-secret-of-at-least-32-characters", Instant.now(), body))
                .andExpect(status().isUnauthorized());
        deliver(body, WebhookSignature.sign(secret, Instant.now().minus(Duration.ofMinutes(10)), body))
                .andExpect(status().isUnauthorized());

        assertThat(paymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(webhookEvents.count()).isZero();
    }

    @Test
    @DisplayName("charge.failed settles a timed-out payment as failed, and the order can be paid again")
    void chargeFailed_settlesTheAttemptAsFailed() throws Exception {
        Long orderId = placeOrder();
        pay(orderId, FakePaymentGateway.TIMEOUT_NOT_CHARGED).andExpect(status().isAccepted());
        Long paymentId = paymentOf(orderId).getId();

        deliver(event("evt_failed", "charge.failed",
                "{\"idempotencyKey\":\"pay_" + paymentId + "\",\"failureReason\":\"Your card was declined.\"}"))
                .andExpect(status().isOk());

        assertThat(paymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(paymentOf(orderId).getFailureReason()).isEqualTo("Your card was declined.");
        pay(orderId, FakePaymentGateway.VISA).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("refund.succeeded settles a refund the provider couldn't confirm at first")
    void refundSucceeded_settlesAPendingRefund() throws Exception {
        Long orderId = placeOrder();
        pay(orderId, FakePaymentGateway.REFUND_DOWN).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        String refundKey = refundRepository.findAllByOrder(orderId).get(0).getIdempotencyKey();
        // Owed: the attempt after the commit is refused by this test card's provider
        // every time it tries (3 calls), so only the webhook can settle it here.
        assertThat(paymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);

        deliver(event("evt_refunded", "refund.succeeded",
                "{\"idempotencyKey\":\"" + refundKey + "\",\"refundReference\":\"re_from_webhook\"}"))
                .andExpect(status().isOk());

        assertThat(paymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refundRepository.findAllByOrder(orderId)).singleElement()
                .satisfies(refund -> assertThat(refund.getProviderReference()).isEqualTo("re_from_webhook"));
    }

    @Test
    @DisplayName("an event the shop doesn't handle is acknowledged; a signed body that isn't an event is 400")
    void unknownEventsAreAcknowledged_andGarbageIsRefused() throws Exception {
        deliver(event("evt_other", "customer.updated", "{}")).andExpect(status().isOk());
        deliver(event("evt_nobody", "charge.succeeded", "{\"idempotencyKey\":\"pay_999999999\"}"))
                .andExpect(status().isOk());
        assertThat(webhookEvents.count()).isEqualTo(2);

        byte[] garbage = "{not json".getBytes(StandardCharsets.UTF_8);
        deliver(garbage, WebhookSignature.sign(secret, Instant.now(), garbage)).andExpect(status().isBadRequest());
    }

    // --- helpers -----------------------------------------------------------

    /** The provider's real record of the charge, reported as charge.succeeded. */
    private String chargeSucceeded(String eventId, Long paymentId) {
        // The fake looks a charge up by its key alone.
        ChargeResult.Approved charge = (ChargeResult.Approved) gateway.findCharge(
                new ChargeRequest("pay_" + paymentId, null, null, null)).orElseThrow();
        return event(eventId, "charge.succeeded", "{\"idempotencyKey\":\"pay_" + paymentId + "\",\"providerReference\":\""
                + charge.providerReference() + "\",\"cardBrand\":\"Visa\",\"cardLast4\":\"4242\"}");
    }

    private static String event(String id, String type, String data) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"created\":" + Instant.now().getEpochSecond()
                + ",\"data\":" + data + "}";
    }

    /** Post an event signed with the shop's secret, as the provider would; no Authorization header. */
    private ResultActions deliver(String event) throws Exception {
        byte[] body = event.getBytes(StandardCharsets.UTF_8);
        return deliver(body, WebhookSignature.sign(secret, Instant.now(), body));
    }

    private ResultActions deliver(byte[] body, String signature) throws Exception {
        return mockMvc.perform(post(WEBHOOKS)
                .contentType(MediaType.APPLICATION_JSON)
                .header(WebhookSignature.HEADER, signature)
                .content(body));
    }

    private void clearData() {
        webhookEvents.deleteAll();
        paymentRepository.deleteAll();  // FK on orders — before the orders
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        refreshTokenRepository.deleteAll();  // FK on users — before the users
        userRepository.deleteAll();
        productRepository.deleteAll();
    }

    private Long placeOrder() throws Exception {
        mockMvc.perform(post("/api/v1/cartItems")
                        .header("Authorization", bearer(token))
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "1"))
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

    private Payment paymentOf(Long orderId) {
        return paymentRepository.findAllByOrder(orderId).get(0);
    }

    private OrderStatus orderStatus(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getOrderStatus();
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"webhooks@example.com\",\"password\":\"" + PASSWORD + "\"}"))
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
