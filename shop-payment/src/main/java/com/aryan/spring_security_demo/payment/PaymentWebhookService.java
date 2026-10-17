package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;


/**
 * Receives a webhook: has the provider's {@link WebhookReader} check that it comes
 * from the provider and read the event, then has {@link PaymentWebhookProcessor}
 * make its change. Not {@code @Transactional}:
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
    private final WebhookReader reader;
    private final PaymentWebhookProcessor processor;
    private final PaymentWebhookEventRepository events;

    public PaymentWebhookProcessor.Outcome receive(WebhookHeaders headers, byte[] body) {
        if (!properties.isWebhooksEnabled()) {
            throw new InvalidWebhookSignatureException("Webhooks are not configured");
        }
        if (body.length > MAX_BODY_BYTES) {
            throw new InvalidWebhookException("The webhook body is too large");
        }
        WebhookEvent event = reader.read(headers, body);
        if (isBlankOrLong(event.id()) || isBlankOrLong(event.type())) {
            throw new InvalidWebhookException("The webhook event needs an id and a type, of at most 64 characters");
        }
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

    private static boolean isBlankOrLong(String value) {
        return value == null || value.isBlank() || value.length() > 64;
    }
}
