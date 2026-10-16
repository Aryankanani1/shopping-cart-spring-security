package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** What happens before an event is processed: the signature, the body, and a colliding duplicate. */
class PaymentWebhookServiceTest {

    private static final String SECRET = "test-only-webhook-secret-0000000000000000000000";
    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");
    private static final byte[] EVENT = ("{\"id\":\"evt_1\",\"type\":\"charge.succeeded\",\"created\":1760616000,"
            + "\"data\":{\"idempotencyKey\":\"pay_7\"},\"addedByTheProviderLater\":true}").getBytes(StandardCharsets.UTF_8);

    private final PaymentProperties properties = new PaymentProperties();
    private final PaymentWebhookProcessor processor = mock(PaymentWebhookProcessor.class);
    private final PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
    private PaymentWebhookService service;

    @BeforeEach
    void setUp() {
        properties.setWebhookSecret(SECRET);
        service = new PaymentWebhookService(properties, processor, events, JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aSignedEvent_isProcessed_unknownFieldsAndAll() {
        when(processor.process(any())).thenReturn(PaymentWebhookProcessor.Outcome.APPLIED);

        assertThat(service.receive(WebhookSignature.sign(SECRET, NOW, EVENT), EVENT))
                .isEqualTo(PaymentWebhookProcessor.Outcome.APPLIED);
    }

    @Test
    void anUnsignedEvent_isRefused_beforeAnyProcessing() {
        assertThatThrownBy(() -> service.receive(null, EVENT)).isInstanceOf(InvalidWebhookSignatureException.class);

        verifyNoInteractions(processor);
    }

    @Test
    void withNoSecretConfigured_everyWebhookIsRefused() {
        properties.setWebhookSecret("");

        assertThatThrownBy(() -> service.receive(WebhookSignature.sign(SECRET, NOW, EVENT), EVENT))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void aSignedBodyThatIsNotAnEvent_is400() {
        for (String body : new String[] {"not json", "{}", "{\"id\":\"evt_1\"}", "{\"id\":\" \",\"type\":\"x\"}"}) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            assertThatThrownBy(() -> service.receive(WebhookSignature.sign(SECRET, NOW, bytes), bytes))
                    .isInstanceOf(InvalidWebhookException.class);
        }
    }

    @Test
    void anOversizedBody_isRefused_beforeTheSignatureIsComputed() {
        byte[] huge = new byte[PaymentWebhookService.MAX_BODY_BYTES + 1];

        assertThatThrownBy(() -> service.receive("t=1,v1=00", huge)).isInstanceOf(InvalidWebhookException.class);
    }

    // Two deliveries at once: the other recorded the event first, and this one rolled back.
    @Test
    void aDuplicateThatCollided_isAnsweredAsDone() {
        when(processor.process(any())).thenThrow(new DataIntegrityViolationException("uk_payment_webhook_event_event_id"));
        when(events.existsByEventId("evt_1")).thenReturn(true);

        assertThat(service.receive(WebhookSignature.sign(SECRET, NOW, EVENT), EVENT))
                .isEqualTo(PaymentWebhookProcessor.Outcome.DUPLICATE);
    }

    @Test
    void anotherIntegrityError_isNotMistakenForADuplicate() {
        when(processor.process(any())).thenThrow(new DataIntegrityViolationException("something else"));
        when(events.existsByEventId("evt_1")).thenReturn(false);

        assertThatThrownBy(() -> service.receive(WebhookSignature.sign(SECRET, NOW, EVENT), EVENT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
