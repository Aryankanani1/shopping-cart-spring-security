package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Stripe's events, signed in Stripe-Signature, as the shop's (docs/payment-design.md, section 8). */
class StripeWebhookReaderTest {

    private static final String SECRET = "whsec_test-only-webhook-secret-00000000000000";
    private static final String INSTALLATION = "6f1c2a9e-0d3b-4c57-9a8e-1b2c3d4e5f60";
    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");
    private static final long CREATED = 1760616000L;

    private final StripeWebhookReader reader = new StripeWebhookReader(properties(), new Installation(null) {
        @Override
        public String id() {
            return INSTALLATION;
        }
    }, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aSucceededPaymentIntent_isAChargeSucceeded_forThePaymentInItsMetadata() {
        WebhookEvent event = read(event("payment_intent.succeeded", """
                {"id": "pi_123", "object": "payment_intent", "status": "succeeded",
                 "metadata": {"shop_installation": "%s", "shop_charge_key": "pay_7"}}""".formatted(INSTALLATION)));

        assertThat(event).isEqualTo(new WebhookEvent("evt_1", WebhookEvent.CHARGE_SUCCEEDED, CREATED,
                new WebhookEvent.Data("pay_7", "pi_123", null, null, null, null)));
    }

    @Test
    void aFailedPaymentIntent_isAChargeFailed_withStripesReason() {
        WebhookEvent event = read(event("payment_intent.payment_failed", """
                {"id": "pi_123", "status": "requires_payment_method",
                 "last_payment_error": {"type": "card_error", "message": "Your card was declined."},
                 "metadata": {"shop_installation": "%s", "shop_charge_key": "pay_7"}}""".formatted(INSTALLATION)));

        assertThat(event).isEqualTo(new WebhookEvent("evt_1", WebhookEvent.CHARGE_FAILED, CREATED,
                new WebhookEvent.Data("pay_7", null, null, null, "Your card was declined.", null)));
    }

    @Test
    void aSucceededRefund_isARefundSucceeded_forThePaymentInItsMetadata() {
        WebhookEvent event = read(event("refund.updated", """
                {"id": "re_1", "object": "refund", "status": "succeeded", "payment_intent": "pi_123",
                 "metadata": {"shop_installation": "%s", "shop_refund_key": "refund_7"}}""".formatted(INSTALLATION)));

        assertThat(event).isEqualTo(new WebhookEvent("evt_1", WebhookEvent.REFUND_SUCCEEDED, CREATED,
                new WebhookEvent.Data("refund_7", "pi_123", null, null, null, "re_1")));
    }

    @Test
    void aRefundStillPending_isNothingYet() {
        WebhookEvent event = read(event("refund.created", """
                {"id": "re_1", "object": "refund", "status": "pending", "payment_intent": "pi_123",
                 "metadata": {"shop_installation": "%s", "shop_refund_key": "refund_7"}}""".formatted(INSTALLATION)));

        assertThat(event.type()).isEqualTo("refund.created");
        assertThat(event.data()).isNull();
    }

    @Test
    void anotherDatabasesPayment_isIgnored() {
        WebhookEvent event = read(event("payment_intent.succeeded", """
                {"id": "pi_9", "status": "succeeded",
                 "metadata": {"shop_installation": "another-database", "shop_charge_key": "pay_7"}}"""));

        assertThat(event.data()).isNull();
    }

    // Stripe's own charge.succeeded, about a Charge, shares a name with the shop's event.
    @Test
    void anEventTheShopDoesntHandle_keepsItsType_withNoData() {
        WebhookEvent event = read(event("charge.succeeded", """
                {"id": "ch_1", "object": "charge", "payment_intent": "pi_123",
                 "metadata": {"shop_installation": "%s"}}""".formatted(INSTALLATION)));

        assertThat(event.type()).isEqualTo("charge.succeeded");
        assertThat(event.data()).isNull();
    }

    @Test
    void aSignatureInTheFakesHeader_isRefused() {
        byte[] body = event("payment_intent.succeeded", "{}");

        assertThatThrownBy(() -> reader.read(new WebhookHeaders(WebhookSignature.sign(SECRET, NOW, body), null), body))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void aSignedBodyThatIsNotJson_isRefused() {
        byte[] body = "not json".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> read(body)).isInstanceOf(InvalidWebhookException.class);
    }

    private WebhookEvent read(byte[] body) {
        return reader.read(new WebhookHeaders(null, WebhookSignature.sign(SECRET, NOW, body)), body);
    }

    private static byte[] event(String type, String object) {
        return """
                {"id": "evt_1", "object": "event", "type": "%s", "created": %d, "livemode": false,
                 "data": {"object": %s}}""".formatted(type, CREATED, object).getBytes(StandardCharsets.UTF_8);
    }

    private static PaymentProperties properties() {
        PaymentProperties properties = new PaymentProperties();
        properties.setWebhookSecret(SECRET);
        return properties;
    }
}
