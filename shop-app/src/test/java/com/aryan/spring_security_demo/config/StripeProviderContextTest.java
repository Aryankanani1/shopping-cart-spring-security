package com.aryan.spring_security_demo.config;

import com.aryan.spring_security_demo.payment.FakePaymentGateway;
import com.aryan.spring_security_demo.payment.FakeWebhookSender;
import com.aryan.spring_security_demo.payment.Installation;
import com.aryan.spring_security_demo.payment.PaymentGateway;
import com.aryan.spring_security_demo.payment.PaymentNotSettledException;
import com.aryan.spring_security_demo.payment.PaymentOutcomeUnknownException;
import com.aryan.spring_security_demo.payment.PaymentProviderCircuitOpenException;
import com.aryan.spring_security_demo.payment.PaymentProviderUnavailableException;
import com.aryan.spring_security_demo.payment.StripePaymentGateway;
import com.aryan.spring_security_demo.payment.StripeWebhookReader;
import com.aryan.spring_security_demo.payment.WebhookReader;
import com.aryan.spring_security_demo.payment.WebhookSignature;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With {@code app.payment.provider=stripe}, the Stripe beans take the fake ones'
 * place, the installation id comes from the migration (V16), and Stripe's signed
 * webhooks reach the shop: the wiring a unit test can't show. Nothing here calls
 * Stripe.
 */
@Tag("integration")
@SpringBootTest(properties = {
        "app.payment.provider=stripe",
        "app.payment.stripe.secret-key=sk_test_context",
        "app.payment.webhook-secret=" + StripeProviderContextTest.SECRET})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StripeProviderContextTest {

    static final String SECRET = "whsec_test-only-context-secret-0000000000000";
    private static final String EVENT_ID = "evt_stripe_context_test";

    @Autowired private ApplicationContext context;
    @Autowired private Installation installation;
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;
    @Autowired private CircuitBreakerRegistry breakers;
    @Autowired private RetryRegistry retries;
    @Autowired private HealthEndpoint health;

    @AfterEach
    void forgetTheEvent() {
        jdbcTemplate.update("delete from payment_webhook_event where event_id = ?", EVENT_ID);
    }

    @Test
    @DisplayName("the Stripe gateway and webhook reader replace the fake provider's beans")
    void theStripeBeansReplaceTheFakeOnes() {
        assertThat(context.getBean(PaymentGateway.class)).isInstanceOf(StripePaymentGateway.class);
        assertThat(context.getBean(WebhookReader.class)).isInstanceOf(StripeWebhookReader.class);
        assertThat(context.getBeanNamesForType(FakePaymentGateway.class)).isEmpty();
        assertThat(context.getBeanNamesForType(FakeWebhookSender.class)).isEmpty();
    }

    @Test
    @DisplayName("the stripe breaker has the settings in application.yml, and counts only Stripe being unwell")
    void theStripeBreaker_hasTheDesignedSettings() {
        CircuitBreakerConfig config = breakers.circuitBreaker("stripe").getCircuitBreakerConfig();

        assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.getSlowCallRateThreshold()).isEqualTo(50f);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(30_000L);
        assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(2);
        // The rules StripePaymentGatewayTest's breaker copies.
        assertThat(config.getRecordExceptionPredicate().test(new PaymentProviderUnavailableException("down"))).isTrue();
        assertThat(config.getRecordExceptionPredicate().test(new PaymentOutcomeUnknownException("timeout"))).isTrue();
        assertThat(config.getRecordExceptionPredicate().test(new IllegalStateException("refund refused"))).isFalse();
        assertThat(config.getIgnoreExceptionPredicate().test(new PaymentNotSettledException("processing"))).isTrue();
    }

    @Test
    @DisplayName("the payment retries have application.yml's settings: 3 tries, 100 then 200 ms, only 'unavailable'")
    void thePaymentRetries_haveTheDesignedSettings() {
        for (String name : new String[] {"payment-charge", "payment-lookup", "payment-refund"}) {
            RetryConfig config = retries.retry(name).getRetryConfig();

            assertThat(config.getMaxAttempts()).as(name).isEqualTo(3);
            Either<Throwable, Object> failure = Either.left(new PaymentProviderUnavailableException("down"));
            assertThat(config.<Object>getIntervalBiFunction().apply(1, failure)).as(name).isEqualTo(100L);
            assertThat(config.<Object>getIntervalBiFunction().apply(2, failure)).as(name).isEqualTo(200L);
            // The rules PaymentGatewayClientTest's retries copy.
            assertThat(config.getExceptionPredicate().test(new PaymentProviderUnavailableException("down"))).isTrue();
            assertThat(config.getExceptionPredicate().test(new PaymentOutcomeUnknownException("timeout"))).isFalse();
            assertThat(config.getExceptionPredicate().test(new PaymentProviderCircuitOpenException("open", null)))
                    .isFalse();
            assertThat(config.getExceptionPredicate().test(new PaymentNotSettledException("processing"))).isFalse();
        }
    }

    @Test
    @DisplayName("the internal calls' retries and breaker have application.yml's settings, retrying only IO errors")
    void theInternalCallsResilience_hasTheDesignedSettings() {
        Either<Throwable, Object> failure = Either.left(new IOException("connection refused"));
        RetryConfig webhook = retries.retry("webhook-delivery").getRetryConfig();
        assertThat(webhook.getMaxAttempts()).isEqualTo(3);
        assertThat(webhook.<Object>getIntervalBiFunction().apply(1, failure)).isEqualTo(1_000L);
        assertThat(webhook.<Object>getIntervalBiFunction().apply(2, failure)).isEqualTo(2_000L);
        assertThat(webhook.getExceptionPredicate().test(new IOException("503"))).isTrue();
        assertThat(webhook.getExceptionPredicate().test(new IllegalStateException("refused with 401"))).isFalse();

        RetryConfig startup = retries.retry("startup-check").getRetryConfig();
        assertThat(startup.getMaxAttempts()).isEqualTo(3);
        assertThat(startup.<Object>getIntervalBiFunction().apply(2, failure)).as("not doubling").isEqualTo(1_000L);

        CircuitBreakerConfig delivery = breakers.circuitBreaker("webhook-delivery").getCircuitBreakerConfig();
        assertThat(delivery.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(delivery.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(delivery.getRecordExceptionPredicate().test(new IOException("503"))).isTrue();
        assertThat(delivery.getRecordExceptionPredicate().test(new IllegalStateException("refused"))).isFalse();
    }

    @Test
    @DisplayName("an open Stripe breaker leaves health and readiness as they were: Stripe down mustn't take the shop down")
    void anOpenBreaker_leavesTheApiHealthyAndReady() {
        CircuitBreaker stripe = breakers.circuitBreaker("stripe");
        Status healthBefore = health.health().getStatus();
        Status readinessBefore = health.healthForPath("readiness").getStatus();
        try {
            stripe.transitionToForcedOpenState();

            assertThat(health.health().getStatus()).isEqualTo(healthBefore);
            assertThat(health.healthForPath("readiness").getStatus()).isEqualTo(readinessBefore);
        } finally {
            stripe.reset();
        }
    }

    @Test
    @DisplayName("the installation id is a UUID the migration gave this database")
    void theInstallationId_comesFromTheMigration() {
        assertThat(installation.id()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("a webhook signed in Stripe-Signature is accepted; the same in the fake's header is 401")
    void stripesWebhooks_areReadFromTheirOwnHeader() throws Exception {
        byte[] body = ("{\"id\": \"" + EVENT_ID + "\", \"object\": \"event\", \"type\": \"customer.created\", "
                + "\"created\": 1760616000, \"data\": {\"object\": {\"id\": \"cus_1\"}}}").getBytes(StandardCharsets.UTF_8);
        String signature = WebhookSignature.sign(SECRET, clock.instant(), body);

        mockMvc.perform(post("/api/v1/payments/webhooks").contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSignature.HEADER, signature).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/payments/webhooks").contentType(MediaType.APPLICATION_JSON)
                        .header(StripeWebhookReader.SIGNATURE_HEADER, signature).content(body))
                .andExpect(status().isOk());
    }
}
