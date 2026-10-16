package com.aryan.spring_security_demo.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * The simulated provider's webhooks: signed events, sent over HTTP to
 * {@code app.payment.fake.webhook-url} about a second after the provider answered,
 * the way a real one reports. Off until both that URL and the webhook secret are
 * set.
 *
 * <p>A real provider retries a delivery that fails, for days. This one tries once:
 * a lost webhook is left to reconciliation and the refund retry job, which is what
 * they are for.
 */
@Component
@Slf4j
public class FakeWebhookSender {

    static final Duration DELAY = Duration.ofSeconds(1);

    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public FakeWebhookSender(PaymentProperties properties, ObjectMapper objectMapper, TaskScheduler scheduler,
                             Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    public boolean isEnabled() {
        return properties.isWebhooksEnabled() && !properties.getFake().getWebhookUrl().isBlank();
    }

    /** Report an event about a second from now; nothing if webhooks are off. */
    public void send(String type, WebhookEvent.Data data) {
        if (!isEnabled()) {
            return;
        }
        WebhookEvent event = new WebhookEvent(
                "evt_" + UUID.randomUUID().toString().replace("-", ""), type, clock.instant().getEpochSecond(), data);
        scheduler.schedule(() -> deliver(event), clock.instant().plus(DELAY));
    }

    /** One signed delivery attempt. */
    void deliver(WebhookEvent event) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(event);
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getFake().getWebhookUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header(WebhookSignature.HEADER,
                            WebhookSignature.sign(properties.getWebhookSecret(), clock.instant(), body))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status / 100 != 2) {
                log.warn("Webhook {} ({}) was refused with {}", event.id(), event.type(), status);
            }
        } catch (IOException e) {
            log.warn("Webhook {} ({}) couldn't be delivered", event.id(), event.type(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Webhook {} ({}) was interrupted", event.id(), event.type(), e);
        }
    }
}
