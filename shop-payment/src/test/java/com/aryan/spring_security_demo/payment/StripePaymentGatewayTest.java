package com.aryan.spring_security_demo.payment;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The Stripe adapter against recorded Stripe answers (MockRestServiceServer): no
 * network and no key. Each test follows a row of docs/payment-design.md, section 8.
 */
class StripePaymentGatewayTest {

    private static final String API = "https://api.stripe.com";
    private static final String INSTALLATION = "6f1c2a9e-0d3b-4c57-9a8e-1b2c3d4e5f60";
    private static final String CREATE_KEY = INSTALLATION + "-pay_7";

    private static final String CREATED = """
            {"id": "pi_123", "object": "payment_intent", "status": "requires_confirmation"}""";
    private static final String SUCCEEDED = """
            {"id": "pi_123", "object": "payment_intent", "status": "succeeded",
             "payment_method": {"id": "pm_1", "card": {"brand": "visa", "last4": "4242"}}}""";
    private static final String CARD_DECLINED = """
            {"error": {"type": "card_error", "code": "card_declined", "decline_code": "generic_decline",
                       "message": "Your card was declined."}}""";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer stripe = MockRestServiceServer.bindTo(builder).build();
    private final CircuitBreaker breaker = CircuitBreaker.of(StripePaymentGateway.BREAKER, breakerConfig());
    private final StripePaymentGateway gateway = new StripePaymentGateway(properties(), new Installation(null) {
        @Override
        public String id() {
            return INSTALLATION;
        }
    }, JsonMapper.builder().build(), builder, breaker);

    @AfterEach
    void everyExpectedRequestWasSent() {
        stripe.verify();
    }

    // --- charge -----------------------------------------------------------

    @Test
    void aCharge_isCreatedThenConfirmed_eachUnderItsOwnKey() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectConfirm().andRespond(withSuccess(SUCCEEDED, MediaType.APPLICATION_JSON));

        assertThat(gateway.charge(visa())).isEqualTo(new ChargeResult.Approved("pi_123", "Visa", "4242"));
    }

    @ParameterizedTest
    @CsvSource({
            "test_visa, pm_card_visa",
            "test_mastercard, pm_card_mastercard",
            "test_declined, pm_card_chargeDeclined",
            "test_insufficient_funds, pm_card_chargeDeclinedInsufficientFunds"})
    void eachTestMethod_isStripesMatchingTestCard(String method, String stripeTestCard) {
        stripe.expect(requestTo(API + "/v1/payment_intents"))
                .andExpect(content().formDataContains(Map.of("payment_method", stripeTestCard)))
                .andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectConfirm().andRespond(withSuccess(SUCCEEDED, MediaType.APPLICATION_JSON));

        gateway.charge(new ChargeRequest("pay_7", new BigDecimal("25.00"), "USD", method));
    }

    @Test
    void aMethodOnlyTheFakeSimulates_isDeclinedWithoutCallingStripe() {
        ChargeRequest timeout = new ChargeRequest("pay_7", new BigDecimal("25.00"), "USD",
                FakePaymentGateway.TIMEOUT_CHARGED);

        assertThat(gateway.charge(timeout)).isEqualTo(new ChargeResult.Declined(StripePaymentGateway.NOT_ON_STRIPE));
    }

    @Test
    void aCardError_isDeclinedWithStripesMessage() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectConfirm().andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                .body(CARD_DECLINED).contentType(MediaType.APPLICATION_JSON));

        assertThat(gateway.charge(visa())).isEqualTo(new ChargeResult.Declined("Your card was declined."));
    }

    // Stripe's other messages are for developers; this one reached the customer once.
    @Test
    void aCreateStripeRefuses_isDeclined_withAPlainReason_sinceNothingWasConfirmed() {
        expectCreate().andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"error": {"type": "invalid_request_error",
                                   "message": "The `payment_method_types` parameter is no longer supported."}}"""));

        assertThat(gateway.charge(visa())).isEqualTo(new ChargeResult.Declined(StripePaymentGateway.REFUSED));
    }

    @Test
    void aCreateThatFails_isUnavailable_andNothingIsConfirmed() {
        expectCreate().andRespond(withServerError());

        assertThatThrownBy(() -> gateway.charge(visa())).isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void aKeyStripeRefuses_isUnavailable() {
        expectCreate().andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": {\"type\": \"invalid_request_error\", \"message\": \"Invalid API Key provided\"}}"));

        assertThatThrownBy(() -> gateway.charge(visa())).isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void aCreateThatTimesOut_isUnavailable_sinceCreatingChargesNothing() {
        expectCreate().andRespond(withException(new HttpTimeoutException("request timed out")));

        assertThatThrownBy(() -> gateway.charge(visa())).isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void aConfirmThatCantConnect_isUnavailable_sinceItNeverLeft() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectConfirm().andRespond(withException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> gateway.charge(visa())).isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void aConfirmThatTimesOut_hasAnUnknownOutcome() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectConfirm().andRespond(withException(new HttpTimeoutException("request timed out")));

        assertThatThrownBy(() -> gateway.charge(visa())).isInstanceOf(PaymentOutcomeUnknownException.class);
    }

    @Test
    void aConfirmThatFails_hasAnUnknownOutcome_sinceStripeMayHaveCharged() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectConfirm().andRespond(withServerError());

        assertThatThrownBy(() -> gateway.charge(visa())).isInstanceOf(PaymentOutcomeUnknownException.class);
    }

    @Test
    void aConfirmRateLimited_isUnavailable() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectConfirm().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> gateway.charge(visa())).isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void aPaymentStillProcessing_hasAnUnknownOutcome() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectConfirm().andRespond(withSuccess(intent("processing"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.charge(visa())).isInstanceOf(PaymentOutcomeUnknownException.class);
    }

    // --- findCharge --------------------------------------------------------

    @Test
    void findCharge_sendsTheSameCreateAgain_andReportsACharge() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectLookup().andRespond(withSuccess(SUCCEEDED, MediaType.APPLICATION_JSON));

        assertThat(gateway.findCharge(visa())).contains(new ChargeResult.Approved("pi_123", "Visa", "4242"));
    }

    @Test
    void findCharge_reportsADecline() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectLookup().andRespond(withSuccess("""
                {"id": "pi_123", "status": "requires_payment_method",
                 "last_payment_error": {"type": "card_error", "message": "Your card has insufficient funds."}}""",
                MediaType.APPLICATION_JSON));

        assertThat(gateway.findCharge(visa())).contains(new ChargeResult.Declined("Your card has insufficient funds."));
    }

    @Test
    void findCharge_cancelsAPaymentNeverConfirmed_soItCanNoLongerCharge() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectLookup().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        stripe.expect(requestTo(API + "/v1/payment_intents/pi_123/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", CREATE_KEY + "-cancel"))
                .andRespond(withSuccess(intent("canceled"), MediaType.APPLICATION_JSON));

        assertThat(gateway.findCharge(visa())).isEmpty();
    }

    @Test
    void findCharge_reportsACancelledPaymentAsNeverReceived() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectLookup().andRespond(withSuccess(intent("canceled"), MediaType.APPLICATION_JSON));

        assertThat(gateway.findCharge(visa())).isEmpty();
    }

    @Test
    void findCharge_whileStripeIsStillSettling_isUnavailable_toAskAgainLater() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectLookup().andRespond(withSuccess(intent("processing"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.findCharge(visa())).isInstanceOf(PaymentNotSettledException.class);
    }

    @Test
    void findCharge_whenTheCancelFails_isUnavailable_toLookAgainLater() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectLookup().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        stripe.expect(requestTo(API + "/v1/payment_intents/pi_123/cancel")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.findCharge(visa())).isInstanceOf(PaymentProviderUnavailableException.class);
    }

    // The create has changed since it was sent (a deploy), so Stripe won't replay it: that
    // says nothing about the charge, and calling it declined could hide one.
    @Test
    void findCharge_whenStripeWontReplayTheCreate_leavesThePaymentForAPerson() {
        expectCreate().andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"error": {"type": "idempotency_error",
                                   "message": "Keys for idempotent requests can only be used with the same parameters."}}"""));

        assertThatThrownBy(() -> gateway.findCharge(visa())).isInstanceOf(PaymentNotSettledException.class);
    }

    @Test
    void findCharge_whenTheLookupFails_isUnavailable() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectLookup().andRespond(withServerError());

        assertThatThrownBy(() -> gateway.findCharge(visa())).isInstanceOf(PaymentProviderUnavailableException.class);
    }

    // --- refund ------------------------------------------------------------

    @Test
    void aRefund_isSentForThePaymentIntent_underItsOwnKey() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", "pi_123");
        form.add("amount", "2500");
        form.add("metadata[shop_installation]", INSTALLATION);
        form.add("metadata[shop_refund_key]", "refund_7");
        stripe.expect(requestTo(API + "/v1/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", INSTALLATION + "-refund_7"))
                .andExpect(content().formData(form))
                .andRespond(withSuccess("{\"id\": \"re_1\", \"object\": \"refund\", \"status\": \"succeeded\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.refund(refund())).isEqualTo(new RefundResult("re_1"));
    }

    @Test
    void aPendingRefund_isAccepted() {
        stripe.expect(requestTo(API + "/v1/refunds"))
                .andRespond(withSuccess("{\"id\": \"re_1\", \"status\": \"pending\"}", MediaType.APPLICATION_JSON));

        assertThat(gateway.refund(refund())).isEqualTo(new RefundResult("re_1"));
    }

    @Test
    void aRefundThatCantReachStripe_isUnavailable_toRetryUnderTheSameKey() {
        stripe.expect(requestTo(API + "/v1/refunds")).andRespond(withException(new HttpTimeoutException("timed out")));

        assertThatThrownBy(() -> gateway.refund(refund())).isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void aRefundStripeRefuses_isAnError_leftForAPerson() {
        stripe.expect(requestTo(API + "/v1/refunds")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": {\"code\": \"charge_already_refunded\", \"message\": \"Charge has already been refunded.\"}}"));

        assertThatThrownBy(() -> gateway.refund(refund()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Charge has already been refunded.");
    }

    @Test
    void aRefundStripeReportsFailed_isAnError_leftForAPerson() {
        stripe.expect(requestTo(API + "/v1/refunds"))
                .andRespond(withSuccess("{\"id\": \"re_1\", \"status\": \"failed\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.refund(refund())).isInstanceOf(IllegalStateException.class);
    }

    // --- circuit breaker (docs/payment-design.md, section 9) ---------------------

    @Test
    void whileStripeKeepsFailing_theBreakerOpens_andCallsStopReachingIt() {
        expectCreate().andRespond(withServerError());
        expectCreate().andRespond(withServerError());
        for (int call = 0; call < 2; call++) {
            assertThatThrownBy(() -> gateway.charge(visa())).isInstanceOf(PaymentProviderUnavailableException.class);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        // No request expected: verify() after each test fails on any that is sent.
        assertThatThrownBy(() -> gateway.charge(visa())).isInstanceOf(PaymentProviderCircuitOpenException.class);
        assertThatThrownBy(() -> gateway.refund(refund())).isInstanceOf(PaymentProviderCircuitOpenException.class);
        assertThat(gateway.unavailableFor()).contains(Duration.ofSeconds(30));
    }

    @Test
    void declines_areStripeAnswering_soTheyDontOpenTheBreaker() {
        for (int call = 0; call < 3; call++) {
            expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
            expectConfirm().andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                    .body(CARD_DECLINED).contentType(MediaType.APPLICATION_JSON));
        }
        for (int call = 0; call < 3; call++) {
            gateway.charge(visa());
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(gateway.unavailableFor()).isEmpty();
    }

    // Reconciliation looks a stuck payment up every minute: those answers mustn't add up.
    @Test
    void aLookupStripeAnswersWithoutSettling_isNotAFailure() {
        expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
        expectLookup().andRespond(withSuccess(intent("processing"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.findCharge(visa())).isInstanceOf(PaymentNotSettledException.class);

        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void aMethodOnlyTheFakeSimulates_isNotACallForTheBreaker() {
        gateway.charge(new ChargeRequest("pay_7", new BigDecimal("25.00"), "USD", FakePaymentGateway.PROVIDER_DOWN));

        assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @Test
    void halfOpen_trialCallsThatSucceed_closeTheBreakerAgain() {
        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();  // as it does by itself after its wait
        for (int call = 0; call < 2; call++) {
            expectCreate().andRespond(withSuccess(CREATED, MediaType.APPLICATION_JSON));
            expectConfirm().andRespond(withSuccess(SUCCEEDED, MediaType.APPLICATION_JSON));
        }
        for (int call = 0; call < 2; call++) {
            gateway.charge(visa());
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void halfOpen_trialCallsThatFail_openTheBreakerAgain() {
        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();
        expectCreate().andRespond(withServerError());
        expectCreate().andRespond(withServerError());
        for (int call = 0; call < 2; call++) {
            assertThatThrownBy(() -> gateway.charge(visa())).isInstanceOf(PaymentProviderUnavailableException.class);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // --- amounts -----------------------------------------------------------

    @Test
    void amounts_goInTheCurrencysSmallestUnit_exactly() {
        assertThat(StripePaymentGateway.minorUnits(new BigDecimal("25.00"), "USD")).isEqualTo(2500);
        assertThat(StripePaymentGateway.minorUnits(new BigDecimal("500"), "JPY")).isEqualTo(500);
        assertThatThrownBy(() -> StripePaymentGateway.minorUnits(new BigDecimal("0.005"), "USD"))
                .isInstanceOf(ArithmeticException.class);
    }

    // --- helpers -----------------------------------------------------------

    /** The create request for payment 7, in full: the same on every try, as Stripe's replay needs. */
    private ResponseActions expectCreate() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", "2500");
        form.add("currency", "usd");
        form.add("payment_method", "pm_card_visa");
        form.add("automatic_payment_methods[enabled]", "true");
        form.add("automatic_payment_methods[allow_redirects]", "never");
        form.add("metadata[shop_installation]", INSTALLATION);
        form.add("metadata[shop_charge_key]", "pay_7");
        return stripe.expect(requestTo(API + "/v1/payment_intents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk_test_unit"))
                .andExpect(header("Stripe-Version", "2026-09-30.endive"))
                .andExpect(header("Idempotency-Key", CREATE_KEY))
                .andExpect(content().formData(form));
    }

    private ResponseActions expectConfirm() {
        return stripe.expect(requestTo(API + "/v1/payment_intents/pi_123/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", CREATE_KEY + "-confirm"))
                .andExpect(content().formData(new LinkedMultiValueMap<>(Map.of(
                        "expand[]", List.of("payment_method")))));
    }

    private ResponseActions expectLookup() {
        return stripe.expect(requestTo(API + "/v1/payment_intents/pi_123?expand%5B%5D=payment_method"))
                .andExpect(method(HttpMethod.GET));
    }

    private static String intent(String status) {
        return "{\"id\": \"pi_123\", \"object\": \"payment_intent\", \"status\": \"" + status + "\"}";
    }

    private static ChargeRequest visa() {
        return new ChargeRequest("pay_7", new BigDecimal("25.00"), "USD", FakePaymentGateway.VISA);
    }

    private static RefundRequest refund() {
        return new RefundRequest("refund_7", "pi_123", new BigDecimal("25.00"), "USD");
    }

    /**
     * application.yml's rules for what counts (the context test checks they match),
     * with a window small enough to fill in a test.
     */
    private static CircuitBreakerConfig breakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordExceptions(PaymentProviderUnavailableException.class, PaymentOutcomeUnknownException.class)
                .ignoreExceptions(PaymentNotSettledException.class)
                .build();
    }

    private static PaymentProperties properties() {
        PaymentProperties properties = new PaymentProperties();
        properties.getStripe().setSecretKey("sk_test_unit");
        return properties;
    }
}
