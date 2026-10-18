package com.aryan.spring_security_demo.payment;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.MissingNode;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Stripe's API in test mode (docs/payment-design.md, section 8), over HTTPS with
 * Spring's {@link RestClient}. A charge is two requests: create a PaymentIntent
 * (Stripe's record of one payment, which charges nothing), then confirm it (which
 * charges the card). Every request carries an idempotency key that starts with this
 * database's {@link Installation} id, so a repeat, by {@link PaymentGatewayClient}'s
 * retries or by {@link #findCharge}, gets Stripe's first answer and never charges
 * twice.
 *
 * <p>The app doesn't start unless the key is a test-mode one
 * ({@link PaymentProperties#isStripeKeyForTestMode()}), so no real money moves.
 *
 * <p>Every call goes through the {@value #BREAKER} circuit breaker (section 9): when
 * Stripe keeps failing, it opens, and calls fail at once with
 * {@link PaymentProviderCircuitOpenException} instead of waiting for timeouts.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stripe")
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    /** Metadata on each PaymentIntent and refund: which database, and which of its payments. */
    static final String INSTALLATION = "shop_installation";
    static final String CHARGE_KEY = "shop_charge_key";
    static final String REFUND_KEY = "shop_refund_key";

    /** The shop's test methods, as Stripe's test PaymentMethods. */
    static final Map<String, String> TEST_CARDS = Map.of(
            FakePaymentGateway.VISA, "pm_card_visa",
            FakePaymentGateway.MASTERCARD, "pm_card_mastercard",
            FakePaymentGateway.DECLINED, "pm_card_chargeDeclined",
            FakePaymentGateway.INSUFFICIENT_FUNDS, "pm_card_chargeDeclinedInsufficientFunds");

    static final String NOT_ON_STRIPE = "This test payment method only works with the simulated provider; "
            + "with Stripe, use test_visa, test_mastercard, test_declined or test_insufficient_funds.";

    /**
     * What the customer is told when Stripe refuses a payment for a reason other than
     * the card. Stripe writes its card errors for customers, but its other messages
     * are for developers (one said "The payment_method_types parameter is no longer
     * supported"), so those go to the log instead.
     */
    static final String REFUSED = "The payment provider couldn't take this payment; please try again later "
            + "or use another method.";

    /** The circuit breaker's name; its settings are under {@code resilience4j.circuitbreaker.instances}. */
    static final String BREAKER = "stripe";

    private static final String INTENTS = "/v1/payment_intents";

    private final RestClient http;
    private final Installation installation;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker breaker;

    @Autowired
    public StripePaymentGateway(PaymentProperties properties, Installation installation, ObjectMapper objectMapper,
                                CircuitBreakerRegistry breakers) {
        this(properties, installation, objectMapper,
                RestClient.builder().requestFactory(requestFactory(properties.getStripe())),
                breakers.circuitBreaker(BREAKER));
    }

    /** Takes the builder and the breaker, so that tests can bind a {@code MockRestServiceServer} and set the breaker up. */
    StripePaymentGateway(PaymentProperties properties, Installation installation, ObjectMapper objectMapper,
                         RestClient.Builder builder, CircuitBreaker breaker) {
        PaymentProperties.Stripe stripe = properties.getStripe();
        this.http = builder.baseUrl(stripe.getApiBase())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + stripe.getSecretKey())
                .defaultHeader("Stripe-Version", stripe.getApiVersion())
                .build();
        this.installation = installation;
        this.objectMapper = objectMapper;
        this.breaker = breaker;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String testCard = TEST_CARDS.get(request.paymentMethod());
        if (testCard == null) {
            return new ChargeResult.Declined(NOT_ON_STRIPE);  // never sent, so not a call for the breaker
        }
        return guarded(() -> chargeOnce(request, testCard));
    }

    @Override
    public Optional<ChargeResult> findCharge(ChargeRequest request) {
        String testCard = TEST_CARDS.get(request.paymentMethod());
        if (testCard == null) {
            return Optional.of(new ChargeResult.Declined(NOT_ON_STRIPE));
        }
        return guarded(() -> lookUp(request, testCard));
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        return guarded(() -> refundOnce(request));
    }

    /** While the breaker is open: at most its wait, after which it lets trial calls through. */
    @Override
    public Optional<Duration> unavailableFor() {
        CircuitBreaker.State state = breaker.getState();
        if (state != CircuitBreaker.State.OPEN && state != CircuitBreaker.State.FORCED_OPEN) {
            return Optional.empty();
        }
        long waitMillis = breaker.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1);
        return Optional.of(Duration.ofMillis(waitMillis));
    }

    /**
     * Runs {@code call} through the breaker, which counts what it threw: only
     * "unavailable" and "outcome unknown" are failures (application.yml). While it is
     * open, {@code call} doesn't run.
     */
    private <T> T guarded(Supplier<T> call) {
        try {
            return breaker.executeSupplier(call);
        } catch (CallNotPermittedException e) {
            throw new PaymentProviderCircuitOpenException(
                    "Stripe isn't being called: its recent calls kept failing (circuit breaker open)", e);
        }
    }

    private ChargeResult chargeOnce(ChargeRequest request, String testCard) {
        Answer created = create(request, testCard);
        if (!created.isSuccess()) {
            return refusedCreate(created);
        }
        Answer confirmed;
        try {
            confirmed = post(INTENTS + "/" + created.text("id") + "/confirm", form("expand[]", "payment_method"),
                    key(request.idempotencyKey()) + "-confirm");
        } catch (ResourceAccessException e) {
            if (neverSent(e)) {
                throw new PaymentProviderUnavailableException("Couldn't connect to Stripe to confirm the payment", e);
            }
            throw new PaymentOutcomeUnknownException("Stripe didn't answer the confirmation", e);
        }
        if (confirmed.isSuccess()) {
            return outcome(confirmed.body());
        }
        if (confirmed.status() >= 500) {
            // Stripe may have charged before it failed.
            throw new PaymentOutcomeUnknownException("Stripe failed while confirming (" + confirmed.status() + ")");
        }
        if (isTransient(confirmed)) {
            throw unavailable(confirmed, "confirm the payment");
        }
        // A card error (402), or another refusal: nothing was charged.
        return new ChargeResult.Declined(declineReason(confirmed, "confirm"));
    }

    /**
     * Sends the charge's create request again, which Stripe answers with the
     * PaymentIntent from the first time, or creates now if the first never arrived:
     * unconfirmed either way, so nothing is charged. Then reads where it got to.
     */
    private Optional<ChargeResult> lookUp(ChargeRequest request, String testCard) {
        Answer created = create(request, testCard);
        if (!created.isSuccess() && "idempotency_error".equals(created.errorType())) {
            // The create request has changed since it was sent (a deploy changed what
            // the adapter sends), so Stripe won't replay it, and that says nothing about
            // whether the card was charged: calling it declined could hide a charge.
            log.error("Payment {} can't be looked up by its key: the create request has changed since it was "
                    + "sent. Settle it by hand from Stripe's dashboard (metadata {} = {})",
                    request.idempotencyKey(), CHARGE_KEY, request.idempotencyKey());
            throw new PaymentNotSettledException("Stripe won't replay the payment's create request");
        }
        if (!created.isSuccess()) {
            return Optional.of(refusedCreate(created));
        }
        String intentId = created.text("id");
        Answer current;
        try {
            current = http.get()
                    .uri(builder -> builder.path(INTENTS + "/{id}").queryParam("expand[]", "payment_method").build(intentId))
                    .exchangeForRequiredValue((req, res) -> new Answer(res.getStatusCode().value(), read(res.getBody())));
        } catch (ResourceAccessException e) {
            throw new PaymentProviderUnavailableException("Couldn't reach Stripe to look up the payment", e);
        }
        if (!current.isSuccess()) {
            throw unavailable(current, "look up the payment");
        }
        JsonNode intent = current.body();
        return switch (text(intent, "status")) {
            case "succeeded" -> Optional.of(approved(intent));
            case "requires_payment_method" -> intent.path("last_payment_error").isObject()
                    ? Optional.of(declined(intent))
                    : neverConfirmed(intentId, request);
            case "requires_confirmation" -> neverConfirmed(intentId, request);
            case "canceled" -> Optional.empty();
            default -> throw new PaymentNotSettledException(
                    "Stripe is still settling the payment (" + text(intent, "status") + ")");
        };
    }

    private RefundResult refundOnce(RefundRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", request.providerReference());
        form.add("amount", Long.toString(minorUnits(request.amount(), request.currency())));
        form.add("metadata[" + INSTALLATION + "]", installation.id());
        form.add("metadata[" + REFUND_KEY + "]", request.idempotencyKey());
        Answer refund;
        try {
            refund = post("/v1/refunds", form, key(request.idempotencyKey()));
        } catch (ResourceAccessException e) {
            // Whatever became of it, sending it again under the same key is safe.
            throw new PaymentProviderUnavailableException("Couldn't reach Stripe to refund the payment", e);
        }
        if (isTransient(refund)) {
            throw unavailable(refund, "refund the payment");
        }
        if (!refund.isSuccess()) {
            throw new IllegalStateException("Stripe refused refund " + request.idempotencyKey() + ": "
                    + refund.errorMessage());
        }
        // Pending: accepted, while the card network carries the money back.
        String status = refund.text("status");
        if (!"succeeded".equals(status) && !"pending".equals(status)) {
            throw new IllegalStateException("Stripe reports refund " + refund.text("id") + " as " + status);
        }
        return new RefundResult(refund.text("id"));
    }

    /** The PaymentIntent for {@code request}, unconfirmed. Creating one charges nothing. */
    private Answer create(ChargeRequest request, String testCard) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", Long.toString(minorUnits(request.amount(), request.currency())));
        form.add("currency", request.currency().toLowerCase(Locale.ROOT));
        form.add("payment_method", testCard);
        // Stripe chooses the payment methods it offers (payment_method_types is gone
        // from its API); never one that redirects, since the shop confirms on the server.
        form.add("automatic_payment_methods[enabled]", "true");
        form.add("automatic_payment_methods[allow_redirects]", "never");
        form.add("metadata[" + INSTALLATION + "]", installation.id());
        form.add("metadata[" + CHARGE_KEY + "]", request.idempotencyKey());
        try {
            return post(INTENTS, form, key(request.idempotencyKey()));
        } catch (ResourceAccessException e) {
            // Nothing is confirmed yet, so whatever became of the request, nothing was charged.
            throw new PaymentProviderUnavailableException("Couldn't reach Stripe to create the payment", e);
        }
    }

    /**
     * Cancel a PaymentIntent whose confirm never arrived, so one still on its way
     * would fail rather than charge; nothing was charged.
     */
    private Optional<ChargeResult> neverConfirmed(String intentId, ChargeRequest request) {
        Answer cancelled;
        try {
            cancelled = post(INTENTS + "/" + intentId + "/cancel", new LinkedMultiValueMap<>(),
                    key(request.idempotencyKey()) + "-cancel");
        } catch (ResourceAccessException e) {
            throw new PaymentProviderUnavailableException("Couldn't reach Stripe to cancel the payment", e);
        }
        if (isTransient(cancelled)) {
            throw unavailable(cancelled, "cancel the unconfirmed payment");
        }
        if (!cancelled.isSuccess()) {
            // Such as confirmed after all, since the lookup: the next run looks again.
            throw new PaymentNotSettledException("Stripe didn't cancel the unconfirmed payment: "
                    + cancelled.errorMessage());
        }
        return Optional.empty();
    }

    /** A create request Stripe didn't accept. Nothing is confirmed by then, so nothing was charged. */
    private ChargeResult refusedCreate(Answer created) {
        if (isTransient(created)) {
            throw unavailable(created, "create the payment");
        }
        return new ChargeResult.Declined(declineReason(created, "create"));
    }

    /** Stripe's own words for a card error, which it writes for customers; for anything else, {@link #REFUSED}. */
    private static String declineReason(Answer refusal, String step) {
        if ("card_error".equals(refusal.errorType())) {
            return refusal.errorMessage();
        }
        log.warn("Stripe refused the payment's {} ({}, {}): {}", step, refusal.status(), refusal.errorType(),
                refusal.errorMessage());
        return REFUSED;
    }

    /** What a PaymentIntent that was just confirmed says happened. */
    private static ChargeResult outcome(JsonNode intent) {
        String status = text(intent, "status");
        return switch (status) {
            case "succeeded" -> approved(intent);
            case "requires_payment_method" -> declined(intent);
            case "processing" -> throw new PaymentOutcomeUnknownException("Stripe is still processing the payment");
            default -> new ChargeResult.Declined("Stripe couldn't complete the payment (" + status + ")");
        };
    }

    private static ChargeResult.Approved approved(JsonNode intent) {
        JsonNode card = intent.path("payment_method").path("card");
        return new ChargeResult.Approved(text(intent, "id"), brand(text(card, "brand")), emptyToNull(text(card, "last4")));
    }

    private static ChargeResult.Declined declined(JsonNode intent) {
        String message = text(intent.path("last_payment_error"), "message");
        return new ChargeResult.Declined(message.isEmpty() ? "Your card was declined." : message);
    }

    /**
     * Answers that say nothing about the request itself: a key Stripe refuses
     * (a typo, or revoked), too many requests, or Stripe failing.
     */
    private static boolean isTransient(Answer answer) {
        int status = answer.status();
        return status == 401 || status == 403 || status == 429 || status >= 500;
    }

    private static PaymentProviderUnavailableException unavailable(Answer answer, String what) {
        if (answer.status() == 401 || answer.status() == 403) {
            // Stripe's message about a refused key quotes part of it, so it isn't repeated.
            log.error("Stripe refused the API key ({}): check STRIPE_SECRET_KEY", answer.status());
            return new PaymentProviderUnavailableException("Stripe couldn't " + what + " (" + answer.status() + ")");
        }
        return new PaymentProviderUnavailableException(
                "Stripe couldn't " + what + " (" + answer.status() + "): " + answer.errorMessage());
    }

    /** Whether a request certainly never reached Stripe: no connection was made. */
    private static boolean neverSent(ResourceAccessException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException
                    || cause instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }

    /** {@code amount} in the currency's smallest unit (cents for USD), exactly. */
    static long minorUnits(BigDecimal amount, String currency) {
        int decimals = Currency.getInstance(currency).getDefaultFractionDigits();
        return amount.movePointRight(decimals).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    /** Stripe's key for one of the shop's: this database's id first, so databases sharing an account can't collide. */
    private String key(String shopKey) {
        return installation.id() + "-" + shopKey;
    }

    private Answer post(String path, MultiValueMap<String, String> form, String idempotencyKey) {
        return http.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Idempotency-Key", idempotencyKey)
                .body(form)
                .exchangeForRequiredValue((req, res) -> new Answer(res.getStatusCode().value(), read(res.getBody())));
    }

    private JsonNode read(InputStream body) throws IOException {
        byte[] bytes = body.readAllBytes();
        if (bytes.length == 0) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(bytes);
        } catch (JacksonException e) {
            return MissingNode.getInstance();  // not JSON, such as a proxy's error page
        }
    }

    private static MultiValueMap<String, String> form(String name, String value) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(name, value);
        return form;
    }

    static String text(JsonNode node, String field) {
        return node.path(field).asString("");
    }

    /** {@code visa} as {@code Visa}, the way the fake provider and the storefront show it. */
    private static String brand(String stripeBrand) {
        if (stripeBrand.isEmpty()) {
            return null;
        }
        return stripeBrand.substring(0, 1).toUpperCase(Locale.ROOT) + stripeBrand.substring(1);
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static JdkClientHttpRequestFactory requestFactory(PaymentProperties.Stripe stripe) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(stripe.getConnectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(stripe.getReadTimeout());
        return factory;
    }

    /** Stripe's answer to one request: its status code, and its JSON body (missing if it had none). */
    private record Answer(int status, JsonNode body) {

        boolean isSuccess() {
            return status >= 200 && status < 300;
        }

        String text(String field) {
            return StripePaymentGateway.text(body, field);
        }

        /** The kind of error: {@code card_error}, {@code invalid_request_error}, {@code idempotency_error}, ... */
        String errorType() {
            return StripePaymentGateway.text(body.path("error"), "type");
        }

        /** Stripe's own explanation, such as {@code Your card was declined.} */
        String errorMessage() {
            String message = StripePaymentGateway.text(body.path("error"), "message");
            return message.isEmpty() ? "Stripe refused the request (" + status + ")" : message;
        }
    }
}
