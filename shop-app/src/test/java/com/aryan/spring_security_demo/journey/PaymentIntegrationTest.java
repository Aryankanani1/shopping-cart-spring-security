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
import com.aryan.spring_security_demo.payment.ChargeRequest;
import com.aryan.spring_security_demo.payment.FakePaymentGateway;
import com.aryan.spring_security_demo.payment.Payment;
import com.aryan.spring_security_demo.payment.PaymentReconciler;
import com.aryan.spring_security_demo.payment.PaymentRepository;
import com.aryan.spring_security_demo.payment.PaymentStatus;
import com.aryan.spring_security_demo.payment.RefundProcessor;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paying for an order and refunding it, through HTTP and against MySQL: what a
 * payment changes, what a repeated request gets, the races a payment has to win
 * (the same request twice at once, two different payments at once, paying while
 * cancelling), the refund a cancellation owes, made after it commits, and the
 * reconciliation of payments whose outcome was lost. The
 * provider is the real {@link FakePaymentGateway}, spied on to count calls and to
 * see whether a transaction was open during each one.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIntegrationTest {

    private static final String PASSWORD = "secret123";
    private static final BigDecimal UNIT_PRICE = new BigDecimal("19.99");
    private static final Instant STARTED = Instant.parse("2026-10-15T12:00:00Z");
    private static final String ADDRESS_BODY = """
            {"recipientName":"Test Shopper","addressLine1":"1 Test St","addressLine2":"",
             "city":"Testville","state":"TS","postalCode":"12345","country":"Testland"}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    /** A refund after a commit can take a moment: it runs on a background thread. */
    private static final Duration REFUND_WAIT = Duration.ofSeconds(10);
    private static final Instant FAR_FUTURE = Instant.parse("2100-01-01T00:00:00Z");

    private final List<Boolean> providerCalledInATransaction = new CopyOnWriteArrayList<>();
    private final List<Boolean> refundCalledInATransaction = new CopyOnWriteArrayList<>();
    private final List<String> refundThreads = new CopyOnWriteArrayList<>();

    @Autowired private MockMvc mockMvc;
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
    @Autowired private RefundProcessor refundProcessor;
    @Autowired private PaymentReconciler reconciler;
    @MockitoSpyBean private FakePaymentGateway gateway;

    private String passwordHash;
    private Long payerId;
    private Long productId;
    private String shopperToken;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        Role customer = role("ROLE_CUSTOMER");
        passwordHash = passwordEncoder.encode(PASSWORD);  // once: BCrypt is slow on purpose
        payerId = saveUser("payer@example.com", customer);
        saveUser("other@example.com", customer);
        saveUser("admin@example.com", role("ROLE_ADMIN"));
        productId = saveProduct();
        shopperToken = login("payer@example.com");

        doAnswer(inv -> {
            providerCalledInATransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            return inv.callRealMethod();
        }).when(gateway).charge(any());
        doAnswer(inv -> {
            refundCalledInATransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            refundThreads.add(Thread.currentThread().getName());
            return inv.callRealMethod();
        }).when(gateway).refund(any());
    }

    // Other test classes delete orders, which a leftover payment would stop.
    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("a test card pays the order's total and the order becomes PAID")
    void pay_withATestCard_paysTheTotal_andTheOrderIsPaid() throws Exception {
        Long orderId = placeOrder(2);

        MvcResult result = pay(orderId, newKey(), FakePaymentGateway.VISA)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(
                        "/api/v1/orders/" + orderId + "/payments/" + paymentIdOf(orderId))))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.cardBrand").value("Visa"))
                .andExpect(jsonPath("$.data.cardLast4").value("4242"))
                .andReturn();

        assertThat(dataOf(result).path("amount").decimalValue()).isEqualByComparingTo("39.98");  // 2 x 19.99
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID);
        assertThat(inventoryOf(productId)).isEqualTo(98);  // still reserved for the order
    }

    @Test
    @DisplayName("the provider is called with no transaction open")
    void pay_callsTheProvider_outsideAnyTransaction() throws Exception {
        pay(placeOrder(1), newKey(), FakePaymentGateway.VISA).andExpect(status().isCreated());

        assertThat(providerCalledInATransaction).containsExactly(false);
    }

    @Test
    @DisplayName("the same request sent again gets the first answer, and the card is charged once")
    void pay_repeatedWithTheSameKey_chargesOnce() throws Exception {
        Long orderId = placeOrder(1);
        String key = newKey();

        Long first = dataOf(pay(orderId, key, FakePaymentGateway.VISA)
                .andExpect(status().isCreated()).andReturn()).path("id").asLong();
        Long again = dataOf(pay(orderId, key, FakePaymentGateway.VISA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED")).andReturn()).path("id").asLong();

        assertThat(again).isEqualTo(first);
        verify(gateway, times(1)).charge(any());
        assertThat(paymentRepository.findAllByOrder(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("the same key with another payment method is refused")
    void pay_sameKeyWithAnotherMethod_is409() throws Exception {
        Long orderId = placeOrder(1);
        String key = newKey();
        pay(orderId, key, FakePaymentGateway.DECLINED).andExpect(status().isCreated());

        pay(orderId, key, FakePaymentGateway.VISA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Idempotency key reused"));
    }

    @Test
    @DisplayName("a double click (the same request twice at once) charges once")
    void pay_sameKeyTwiceAtOnce_chargesOnce() throws Exception {
        Long orderId = placeOrder(1);
        String key = newKey();

        List<MvcResult> results = allAtOnce(List.of(
                () -> pay(orderId, key, FakePaymentGateway.VISA).andReturn(),
                () -> pay(orderId, key, FakePaymentGateway.VISA).andReturn()));

        assertThat(statusesOf(results)).containsExactlyInAnyOrder(201, 200);
        verify(gateway, times(1)).charge(any());
        assertThat(paymentRepository.findAllByOrder(orderId)).hasSize(1);
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("of several different payments for one order at once, exactly one is charged")
    void pay_differentKeysAtOnce_chargesExactlyOne() throws Exception {
        Long orderId = placeOrder(1);
        List<Callable<MvcResult>> payments = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            payments.add(() -> pay(orderId, newKey(), FakePaymentGateway.VISA).andReturn());
        }

        List<Integer> statuses = statusesOf(allAtOnce(payments));

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
        verify(gateway, times(1)).charge(any());
        assertThat(paymentRepository.findAllByOrder(orderId))
                .extracting(Payment::getStatus).containsExactly(PaymentStatus.SUCCEEDED);
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("a declined card leaves the order pending, and another card then pays it")
    void pay_declined_thenAnotherCard_pays() throws Exception {
        Long orderId = placeOrder(1);

        pay(orderId, newKey(), FakePaymentGateway.DECLINED)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Payment failed"))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureReason").value("Your card was declined."));
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PENDING);

        pay(orderId, newKey(), FakePaymentGateway.MASTERCARD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID);

        mockMvc.perform(get("/api/v1/orders/{orderId}/payments", orderId).header("Authorization", bearer(shopperToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data[1].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("a provider that stays down is tried three times, then the attempt fails")
    void pay_providerDown_isRetried_thenFails() throws Exception {
        Long orderId = placeOrder(1);

        pay(orderId, newKey(), FakePaymentGateway.PROVIDER_DOWN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureReason").value(startsWith(
                        "The payment provider is unavailable")));

        verify(gateway, times(3)).charge(any());
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("a provider that fails once is retried with the same key, and charges once")
    void pay_flakyProvider_isRetriedWithTheSameKey() throws Exception {
        Long orderId = placeOrder(1);
        List<String> keysSent = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            keysSent.add(inv.<ChargeRequest>getArgument(0).idempotencyKey());
            return inv.callRealMethod();
        }).when(gateway).charge(any());

        pay(orderId, newKey(), FakePaymentGateway.FLAKY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        assertThat(keysSent).hasSize(2).containsOnly("pay_" + paymentIdOf(orderId));
    }

    @Test
    @DisplayName("after five failed attempts the order can't be paid any more")
    void pay_afterFiveFailures_is409() throws Exception {
        Long orderId = placeOrder(1);
        for (int i = 0; i < 5; i++) {
            pay(orderId, newKey(), FakePaymentGateway.INSUFFICIENT_FUNDS).andExpect(status().isCreated());
        }

        pay(orderId, newKey(), FakePaymentGateway.VISA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Too many payment attempts"));
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("an order already paid can't be paid again")
    void pay_anOrderAlreadyPaid_is409() throws Exception {
        Long orderId = placeOrder(1);
        pay(orderId, newKey(), FakePaymentGateway.VISA).andExpect(status().isCreated());

        pay(orderId, newKey(), FakePaymentGateway.VISA).andExpect(status().isConflict());

        verify(gateway, times(1)).charge(any());
    }

    @Test
    @DisplayName("someone else's order can't be paid or seen; an unknown order is 404")
    void someoneElsesOrder_isRefused() throws Exception {
        Long orderId = placeOrder(1);
        String other = login("other@example.com");

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                        .header("Authorization", bearer(other))
                        .header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"test_visa\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/{orderId}/payments", orderId).header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
        pay(orderId + 1000, newKey(), FakePaymentGateway.VISA).andExpect(status().isNotFound());

        assertThat(paymentRepository.count()).isZero();
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("paying needs a token")
    void pay_withoutAToken_is401() throws Exception {
        Long orderId = placeOrder(1);

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                        .header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"test_visa\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an order can't be cancelled while its payment is in progress")
    void cancel_whileAPaymentIsInProgress_is409() throws Exception {
        Long orderId = placeOrder(1);
        // A payment the provider hasn't answered yet (as if the app stopped mid-way).
        Order order = orderRepository.findById(orderId).orElseThrow();
        paymentRepository.save(Payment.start(order, newKey(), FakePaymentGateway.VISA, "USD", STARTED));

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId).header("Authorization", bearer(shopperToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Payment in progress"));

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PENDING);
        assertThat(inventoryOf(productId)).isEqualTo(99);  // not restocked
    }

    @Test
    @DisplayName("paying and cancelling at once never leave a cancelled order charged")
    void payAndCancelAtOnce_neverLeaveACancelledOrderCharged() throws Exception {
        for (int round = 0; round < 5; round++) {
            Long orderId = placeOrder(1);

            List<Integer> statuses = statusesOf(allAtOnce(List.of(
                    () -> pay(orderId, newKey(), FakePaymentGateway.VISA).andReturn(),
                    () -> mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                            .header("Authorization", bearer(shopperToken))).andReturn())));

            OrderStatus status = orderStatus(orderId);
            List<PaymentStatus> payments = paymentRepository.findAllByOrder(orderId).stream()
                    .map(Payment::getStatus).toList();
            if (statuses.get(0) == 201 && statuses.get(1) == 200) {
                // The payment finished first and the cancellation came after it: the
                // refund was owed in the same commit as the cancellation.
                assertThat(status).isEqualTo(OrderStatus.CANCELLED);
                assertThat(payments).as("round %d", round)
                        .singleElement().isIn(PaymentStatus.REFUND_PENDING, PaymentStatus.REFUNDED);
                awaitPayment(orderId, PaymentStatus.REFUNDED);
            } else if (statuses.get(0) == 201) {
                // Refused while the payment was in progress, by the listener or its version check.
                assertThat(statuses.get(1)).as("round %d", round).isEqualTo(409);
                assertThat(status).isEqualTo(OrderStatus.PAID);
                assertThat(payments).containsExactly(PaymentStatus.SUCCEEDED);
            } else {
                assertThat(statuses).as("round %d", round).containsExactly(409, 200);
                assertThat(status).isEqualTo(OrderStatus.CANCELLED);
                assertThat(payments).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("an admin can't mark an order paid by hand, nor fulfil an unpaid one")
    void admin_cannotMarkPaid_norFulfilAnUnpaidOrder() throws Exception {
        Long orderId = placeOrder(1);
        String admin = login("admin@example.com");

        for (String target : new String[] {"PAID", "PROCESSING"}) {
            mockMvc.perform(patch("/api/v1/orders/{orderId}/status", orderId)
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + target + "\"}"))
                    .andExpect(status().isConflict());
        }
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("cancelling a paid order refunds it after the commit, on another thread, with no transaction open")
    void cancel_aPaidOrder_refundsIt() throws Exception {
        Long orderId = placeOrder(2);
        String key = newKey();
        pay(orderId, key, FakePaymentGateway.VISA).andExpect(status().isCreated());
        assertThat(inventoryOf(productId)).isEqualTo(98);

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId).header("Authorization", bearer(shopperToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        Payment refunded = awaitPayment(orderId, PaymentStatus.REFUNDED);
        assertThat(refunded.getRefundReference()).startsWith("re_");
        assertThat(refunded.getRefundedAt()).isNotNull();
        assertThat(inventoryOf(productId)).isEqualTo(100);
        assertThat(refundCalledInATransaction).containsExactly(false);
        assertThat(refundThreads).singleElement().isNotEqualTo(Thread.currentThread().getName());

        // Repeating the original payment request now finds it refunded.
        pay(orderId, key, FakePaymentGateway.VISA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment refunded"))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
    }

    @Test
    @DisplayName("a refund the provider can't take yet stays owed, and the retry job refunds it once")
    void refund_whileTheProviderIsDown_isRetriedByTheJob() throws Exception {
        Long orderId = placeOrder(1);
        pay(orderId, newKey(), FakePaymentGateway.REFUND_DOWN).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId).header("Authorization", bearer(shopperToken)))
                .andExpect(status().isOk());
        // The attempt after the commit: a first try and 2 retries, all refused.
        await().atMost(REFUND_WAIT).until(() -> refundThreads.size() == 3);
        assertThat(paymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);

        assertThat(refundProcessor.retryPendingRefunds(FAR_FUTURE)).isEqualTo(1);

        assertThat(paymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refundThreads).hasSize(4);
        // Nothing left to retry, and no second refund.
        assertThat(refundProcessor.retryPendingRefunds(FAR_FUTURE)).isZero();
        assertThat(refundThreads).hasSize(4);
    }

    @Test
    @DisplayName("an admin cancelling an order being fulfilled refunds it")
    void admin_cancellingAProcessingOrder_refundsIt() throws Exception {
        Long orderId = placeOrder(1);
        pay(orderId, newKey(), FakePaymentGateway.MASTERCARD).andExpect(status().isCreated());
        String admin = login("admin@example.com");

        for (String target : new String[] {"PROCESSING", "CANCELLED"}) {
            mockMvc.perform(patch("/api/v1/orders/{orderId}/status", orderId)
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + target + "\"}"))
                    .andExpect(status().isOk());
        }

        awaitPayment(orderId, PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("deleting an account refunds its paid orders")
    void deletingTheAccount_refundsItsPaidOrders() throws Exception {
        Long orderId = placeOrder(1);
        pay(orderId, newKey(), FakePaymentGateway.VISA).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/users/{userId}", payerId).header("Authorization", bearer(shopperToken)))
                .andExpect(status().is2xxSuccessful());

        awaitPayment(orderId, PaymentStatus.REFUNDED);
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("a timeout after the provider charged: 202, then reconciliation finds the charge and the order is paid")
    void timeoutAfterCharging_isReconciledToPaid() throws Exception {
        Long orderId = placeOrder(1);

        pay(orderId, newKey(), FakePaymentGateway.TIMEOUT_CHARGED)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
        // Not retried inside the request: a timeout could take its whole length again.
        verify(gateway, times(1)).charge(any());
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PENDING);
        // Meanwhile the order can be neither paid again nor cancelled.
        pay(orderId, newKey(), FakePaymentGateway.VISA).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId).header("Authorization", bearer(shopperToken)))
                .andExpect(status().isConflict());

        assertThat(reconciler.reconcileStuckPayments(FAR_FUTURE)).isEqualTo(1);

        Payment settled = paymentOf(orderId);
        assertThat(settled.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(settled.getProviderReference()).startsWith("ch_");
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID);
        verify(gateway).findCharge("pay_" + settled.getId());
        verify(gateway, times(1)).charge(any());  // looked up, never charged again
    }

    @Test
    @DisplayName("a timeout before the provider got the charge: reconciliation fails it, and the order can be paid")
    void timeoutBeforeCharging_isReconciledToFailed() throws Exception {
        Long orderId = placeOrder(1);
        pay(orderId, newKey(), FakePaymentGateway.TIMEOUT_NOT_CHARGED).andExpect(status().isAccepted());

        assertThat(reconciler.reconcileStuckPayments(FAR_FUTURE)).isEqualTo(1);

        assertThat(paymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(paymentOf(orderId).getFailureReason()).contains("never reached the provider");
        pay(orderId, newKey(), FakePaymentGateway.VISA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    @Test
    @DisplayName("the app stopped after the provider charged: reconciliation records the charge")
    void crashAfterTheProviderCharged_isReconciledToPaid() throws Exception {
        Long orderId = placeOrder(1);
        Order order = orderRepository.findById(orderId).orElseThrow();
        // The attempt was recorded and the provider charged it, but the answer was
        // never recorded (as if the app had stopped in between).
        Payment stuck = paymentRepository.save(
                Payment.start(order, newKey(), FakePaymentGateway.VISA, "USD", STARTED));
        gateway.charge(new ChargeRequest("pay_" + stuck.getId(), stuck.getAmount(), "USD", FakePaymentGateway.VISA));

        assertThat(reconciler.reconcile(stuck.getId())).isTrue();

        assertThat(paymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("reconciliation leaves attempts younger than its cutoff to their own request")
    void reconciliation_leavesFreshAttemptsAlone() throws Exception {
        Long orderId = placeOrder(1);
        pay(orderId, newKey(), FakePaymentGateway.TIMEOUT_NOT_CHARGED).andExpect(status().isAccepted());

        assertThat(reconciler.reconcileStuckPayments(Instant.parse("2000-01-01T00:00:00Z"))).isZero();

        assertThat(paymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        verify(gateway, never()).findCharge(any());
    }

    // The backstop under the order lock: whatever the code does, the database keeps
    // a second payment for an order from being in progress or succeeding.
    @Test
    @DisplayName("the database allows one payment per order that hasn't failed")
    void database_allowsOneLivePaymentPerOrder() throws Exception {
        Order order = orderRepository.findById(placeOrder(1)).orElseThrow();
        Payment failed = Payment.start(order, newKey(), FakePaymentGateway.DECLINED, "USD", STARTED);
        failed.fail("Your card was declined.", STARTED);
        paymentRepository.save(failed);
        paymentRepository.save(Payment.start(order, newKey(), FakePaymentGateway.VISA, "USD", STARTED));

        assertThatThrownBy(() -> paymentRepository.save(
                Payment.start(order, newKey(), FakePaymentGateway.VISA, "USD", STARTED)))
                .isInstanceOf(DataIntegrityViolationException.class);
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

    private Role role(String name) {
        return roleRepository.findByName(name).orElseGet(() -> roleRepository.save(new Role(name)));
    }

    private Long saveUser(String email, Role role) {
        User user = new User();
        user.setFirstName("Pat");
        user.setLastName("Payer");
        user.setEmail(email);
        user.setPassword(passwordHash);
        user.setRoles(Set.of(role));
        return userRepository.save(user).getId();
    }

    /** A product at {@link #UNIT_PRICE} with 100 in stock. */
    private Long saveProduct() {
        Category category = categoryRepository.existsByName("Electronics")
                ? categoryRepository.findByName("Electronics")
                : categoryRepository.save(new Category("Electronics"));
        return productRepository.save(new Product("Card Reader", UNIT_PRICE, "", "Acme", 100, category)).getId();
    }

    /** Check out {@code quantity} of the product as the shopper; returns the new order's id. */
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

    private ResultActions pay(Long orderId, String key, String paymentMethod) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .header("Authorization", bearer(shopperToken))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\":\"" + paymentMethod + "\"}"));
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** The id of the order's only payment. */
    private Long paymentIdOf(Long orderId) {
        return paymentOf(orderId).getId();
    }

    private Payment paymentOf(Long orderId) {
        return paymentRepository.findAllByOrder(orderId).get(0);
    }

    /** Waits, with a time limit, for the refund that runs after a commit to settle the payment. */
    private Payment awaitPayment(Long orderId, PaymentStatus expected) {
        await().atMost(REFUND_WAIT).until(() -> paymentOf(orderId).getStatus() == expected);
        return paymentOf(orderId);
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

    /** The {@code data} node of an {@code ApiResponse} JSON body. */
    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
