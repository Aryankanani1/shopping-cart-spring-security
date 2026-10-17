package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * Stripe's webhooks (docs/payment-design.md, section 8). Stripe signs them the way
 * the fake does, in a {@code Stripe-Signature} header, with the {@code whsec_...}
 * secret in {@code PAYMENT_WEBHOOK_SECRET}. Its events become the shop's:
 *
 * <ul>
 *   <li>{@code payment_intent.succeeded}: {@value WebhookEvent#CHARGE_SUCCEEDED}</li>
 *   <li>{@code payment_intent.payment_failed}: {@value WebhookEvent#CHARGE_FAILED}</li>
 *   <li>{@code refund.created} or {@code refund.updated}, once the refund has
 *       succeeded: {@value WebhookEvent#REFUND_SUCCEEDED}</li>
 * </ul>
 *
 * The payment is the one named in the PaymentIntent's or the refund's metadata. Any
 * other event, or one whose metadata names another database's installation, keeps
 * its type and has no data, which the processor records and ignores (Stripe also
 * sends a {@code charge.succeeded} of its own, about a Charge, which is ignored the
 * same way).
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stripe")
@RequiredArgsConstructor
public class StripeWebhookReader implements WebhookReader {

    public static final String SIGNATURE_HEADER = "Stripe-Signature";

    private final PaymentProperties properties;
    private final Installation installation;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public WebhookEvent read(WebhookHeaders headers, byte[] body) {
        WebhookSignature.verify(properties.getWebhookSecret(), headers.stripeSignature(), body, clock.instant(),
                properties.getWebhookTolerance());
        JsonNode event;
        try {
            event = objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new InvalidWebhookException("The webhook body isn't an event");
        }
        String id = StripePaymentGateway.text(event, "id");
        String type = StripePaymentGateway.text(event, "type");
        long created = event.path("created").asLong(0);
        JsonNode object = event.path("data").path("object");
        if (!installation.id().equals(metadata(object, StripePaymentGateway.INSTALLATION))) {
            return new WebhookEvent(id, type, created, null);  // another database's, or not one of ours
        }
        return switch (type) {
            case "payment_intent.succeeded" -> new WebhookEvent(id, WebhookEvent.CHARGE_SUCCEEDED, created,
                    new WebhookEvent.Data(metadata(object, StripePaymentGateway.CHARGE_KEY),
                            StripePaymentGateway.text(object, "id"), null, null, null, null));
            case "payment_intent.payment_failed" -> new WebhookEvent(id, WebhookEvent.CHARGE_FAILED, created,
                    new WebhookEvent.Data(metadata(object, StripePaymentGateway.CHARGE_KEY), null, null, null,
                            failureReason(object), null));
            case "refund.created", "refund.updated" -> "succeeded".equals(StripePaymentGateway.text(object, "status"))
                    ? new WebhookEvent(id, WebhookEvent.REFUND_SUCCEEDED, created,
                            new WebhookEvent.Data(metadata(object, StripePaymentGateway.REFUND_KEY),
                                    StripePaymentGateway.text(object, "payment_intent"), null, null, null,
                                    StripePaymentGateway.text(object, "id")))
                    : new WebhookEvent(id, type, created, null);
            default -> new WebhookEvent(id, type, created, null);
        };
    }

    /** Stripe's reason, or none (the processor then gives its own). */
    private static String failureReason(JsonNode intent) {
        String message = StripePaymentGateway.text(intent.path("last_payment_error"), "message");
        return message.isEmpty() ? null : message;
    }

    private static String metadata(JsonNode object, String key) {
        return StripePaymentGateway.text(object.path("metadata"), key);
    }
}
