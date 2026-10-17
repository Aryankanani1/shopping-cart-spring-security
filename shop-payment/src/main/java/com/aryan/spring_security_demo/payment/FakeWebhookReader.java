package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/** The simulated provider's webhooks ({@link FakeWebhookSender}): already in the shop's format. */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "fake", matchIfMissing = true)
@RequiredArgsConstructor
public class FakeWebhookReader implements WebhookReader {

    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public WebhookEvent read(WebhookHeaders headers, byte[] body) {
        WebhookSignature.verify(properties.getWebhookSecret(), headers.paymentSignature(), body, clock.instant(),
                properties.getWebhookTolerance());
        try {
            return objectMapper.readValue(body, WebhookEvent.class);
        } catch (JacksonException e) {
            throw new InvalidWebhookException("The webhook body isn't an event");
        }
    }
}
