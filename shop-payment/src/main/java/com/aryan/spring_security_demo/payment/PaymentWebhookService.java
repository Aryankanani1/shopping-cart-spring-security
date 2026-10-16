package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * Receives a webhook: checks that it comes from the provider, reads the event, and
 * has {@link PaymentWebhookProcessor} make its change. Not {@code @Transactional}:
 * the signature and the body are checked before any database work, and a
 * duplicate that collided is recognised after its transaction has rolled back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookService {

    /** Far above any real event; refused before the signature is even computed. */
    static final int MAX_BODY_BYTES = 64 * 1024;

    private final PaymentProperties properties;
    private final PaymentWebhookProcessor processor;
    private final PaymentWebhookEventRepository events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaymentWebhookProcessor.Outcome receive(String signature, byte[] body) {
        if (!properties.isWebhooksEnabled()) {
            throw new InvalidWebhookSignatureException("Webhooks are not configured");
        }
        if (body.length > MAX_BODY_BYTES) {
            throw new InvalidWebhookException("The webhook body is too large");
        }
        WebhookSignature.verify(properties.getWebhookSecret(), signature, body, clock.instant(),
                properties.getWebhookTolerance());
        WebhookEvent event = parse(body);
        try {
            PaymentWebhookProcessor.Outcome outcome = processor.process(event);
            log.info("Webhook {} ({}): {}", event.id(), event.type(), outcome);
            return outcome;
        } catch (DataIntegrityViolationException e) {
            // Another delivery of this event recorded its id first (the unique key),
            // and this one was rolled back: the event has been processed.
            if (!events.existsByEventId(event.id())) {
                throw e;
            }
            log.info("Webhook {} was processed by a delivery running at the same time", event.id());
            return PaymentWebhookProcessor.Outcome.DUPLICATE;
        }
    }

    private WebhookEvent parse(byte[] body) {
        WebhookEvent event;
        try {
            event = objectMapper.readValue(body, WebhookEvent.class);
        } catch (JacksonException e) {
            throw new InvalidWebhookException("The webhook body isn't an event");
        }
        if (isBlankOrLong(event.id()) || isBlankOrLong(event.type())) {
            throw new InvalidWebhookException("The webhook event needs an id and a type, of at most 64 characters");
        }
        return event;
    }

    private static boolean isBlankOrLong(String value) {
        return value == null || value.isBlank() || value.length() > 64;
    }
}
