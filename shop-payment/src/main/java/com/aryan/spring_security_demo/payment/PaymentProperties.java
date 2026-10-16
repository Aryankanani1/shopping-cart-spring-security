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

    /**
     * The secret the provider signs its webhooks with ({@code PAYMENT_WEBHOOK_SECRET}),
     * and the shop checks them with. Empty (the default) turns webhooks off: every
     * webhook request is refused, and the fake provider sends none.
     */
    private String webhookSecret = "";

    /** How far a webhook's signed timestamp may be from the shop's clock before it is refused as a replay. */
    private Duration webhookTolerance = Duration.ofMinutes(5);

    private final Fake fake = new Fake();

    public boolean isWebhooksEnabled() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    @AssertTrue(message = "PAYMENT_WEBHOOK_SECRET must be empty or at least 32 characters")
    public boolean isWebhookSecretUsable() {
        return !isWebhooksEnabled() || webhookSecret.length() >= MIN_SECRET_LENGTH;
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
}
