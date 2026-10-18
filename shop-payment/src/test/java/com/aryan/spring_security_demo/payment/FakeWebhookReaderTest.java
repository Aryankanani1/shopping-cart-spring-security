package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The simulated provider's events, signed in Payment-Signature, already in the shop's format. */
class FakeWebhookReaderTest {

    private static final String SECRET = "test-only-webhook-secret-0000000000000000";
    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    private final FakeWebhookReader reader =
            new FakeWebhookReader(properties(), JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aSignedEvent_isRead() {
        WebhookEvent event = read("""
                {"id": "evt_1", "type": "charge.succeeded", "created": 1760788800,
                 "data": {"idempotencyKey": "pay_7", "providerReference": "ch_1"}}""");

        assertThat(event).isEqualTo(new WebhookEvent("evt_1", WebhookEvent.CHARGE_SUCCEEDED, 1760788800L,
                new WebhookEvent.Data("pay_7", "ch_1", null, null, null, null)));
    }

    // Regression (found by the staging fuzzing, once it signed its deliveries): a
    // signed body of null read as no event, and the service's next step was a 500.
    @Test
    void aSignedNull_isntAnEvent() {
        assertThatThrownBy(() -> read("null"))
                .isInstanceOf(InvalidWebhookException.class)
                .hasMessage("The webhook body isn't an event");
    }

    @Test
    void aSignedBodyThatIsntJson_isntAnEvent() {
        assertThatThrownBy(() -> read("{not json"))
                .isInstanceOf(InvalidWebhookException.class)
                .hasMessage("The webhook body isn't an event");
    }

    private WebhookEvent read(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        return reader.read(new WebhookHeaders(WebhookSignature.sign(SECRET, NOW, body), null), body);
    }

    private static PaymentProperties properties() {
        PaymentProperties properties = new PaymentProperties();
        properties.setWebhookSecret(SECRET);
        return properties;
    }
}
