package com.aryan.spring_security_demo.payment;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Payment settings ({@code app.payment.*}). */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.payment")
public class PaymentProperties {

    /** Long enough that it can't be guessed; {@code openssl rand -hex 32} gives 64 characters. */
    private static final int MIN_SECRET_LENGTH = 32;

    /** The payment providers the shop can use ({@code APP_PAYMENT_PROVIDER}). */
    public enum Provider {
        /** Simulated in the app ({@link FakePaymentGateway}): the default, and what the tests, CI and staging use. */
        FAKE,
        /** Stripe in test mode ({@link StripePaymentGateway}). */
        STRIPE
    }

    private Provider provider = Provider.FAKE;

    /**
     * The secret the provider signs its webhooks with ({@code PAYMENT_WEBHOOK_SECRET}),
     * and the shop checks them with. Empty (the default) turns webhooks off: every
     * webhook request is refused, and the fake provider sends none.
     */
    private String webhookSecret = "";

    /** How far a webhook's signed timestamp may be from the shop's clock before it is refused as a replay. */
    private Duration webhookTolerance = Duration.ofMinutes(5);

    private final Fake fake = new Fake();

    private final Stripe stripe = new Stripe();

    public boolean isWebhooksEnabled() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    @AssertTrue(message = "PAYMENT_WEBHOOK_SECRET must be empty or at least 32 characters")
    public boolean isWebhookSecretUsable() {
        return !isWebhooksEnabled() || webhookSecret.length() >= MIN_SECRET_LENGTH;
    }

    /**
     * Stripe is used in test mode only, so this project can't move real money however
     * it is configured: startup fails unless its key is a test-mode one.
     */
    @AssertTrue(message = "STRIPE_SECRET_KEY must be a Stripe test-mode key (sk_test_... or rk_test_...) "
            + "when APP_PAYMENT_PROVIDER is stripe")
    public boolean isStripeKeyForTestMode() {
        return provider != Provider.STRIPE || stripe.isTestModeKey();
    }

    /** The simulated provider ({@link FakePaymentGateway}). */
    @Getter
    @Setter
    public static class Fake {

        /**
         * Where it delivers its webhooks ({@code APP_PAYMENT_FAKE_WEBHOOK_URL}): the
         * API's own webhook endpoint. Empty (the default) means it sends none.
         */
        private String webhookUrl = "";

        /**
         * How long each of its calls takes ({@code APP_PAYMENT_FAKE_LATENCY}), as a
         * real provider's network round trip would. Zero (the default) answers at
         * once; the load test sets it to show that a slow provider doesn't drain
         * the connection pool, since no transaction is open during its calls.
         */
        private Duration latency = Duration.ZERO;
    }

    /** Stripe in test mode ({@link StripePaymentGateway}). */
    @Getter
    @Setter
    public static class Stripe {

        /**
         * A test-mode secret key ({@code STRIPE_SECRET_KEY}), from the Stripe dashboard
         * (Developers, API keys). Only from the environment: never in a committed file.
         */
        private String secretKey = "";

        /** Where Stripe's API is; tests point it elsewhere. */
        private String apiBase = "https://api.stripe.com";

        /**
         * The API version the adapter was written and tried against, sent on every
         * request. Without it Stripe uses the account's default, which changes when
         * the account is upgraded and can drop a parameter the adapter sends.
         */
        private String apiVersion = "2026-09-30.endive";

        /** How long to wait for a connection: past it, nothing was sent. */
        private Duration connectTimeout = Duration.ofSeconds(5);

        /** How long to wait for an answer: past it, Stripe may have acted on the request. */
        private Duration readTimeout = Duration.ofSeconds(30);

        boolean isTestModeKey() {
            return secretKey != null && (secretKey.startsWith("sk_test_") || secretKey.startsWith("rk_test_"));
        }
    }
}
