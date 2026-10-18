package com.aryan.spring_security_demo.payment;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The simulated provider's webhooks: signed events, sent over HTTP to
 * {@code app.payment.fake.webhook-url} about a second after the provider answered,
 * the way a real one reports. Off until both that URL and the webhook secret are
 * set.
 *
 * <p>A real provider retries a delivery that fails, for days. This one tries three
 * times (Resilience4j's {@value #RETRY} retry: on a connection error or a {@code 5xx},
 * 1 s and then 2 s apart), through the {@value #BREAKER} circuit breaker, which skips
 * deliveries for a while once they keep failing (docs/payment-design.md, section
 * 10). Then the fallback logs it: reconciliation and the refund retry job settle
 * what the webhook would have.
 *
 * <p>Deliveries run on the scheduler, whose one thread also runs the payment jobs,
 * so a retry never waits on it: the retry says how long to wait, and the next try
 * is scheduled for then; the request itself is sent asynchronously.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "fake", matchIfMissing = true)
@Slf4j
public class FakeWebhookSender {

    static final Duration DELAY = Duration.ofSeconds(1);

    /** The retry's and the breaker's name; their settings are in {@code application.yml}. */
    static final String RETRY = "webhook-delivery";
    static final String BREAKER = "webhook-delivery";

    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final Retry retry;
    private final CircuitBreaker breaker;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    public FakeWebhookSender(PaymentProperties properties, ObjectMapper objectMapper, TaskScheduler scheduler,
                             Clock clock, RetryRegistry retries, CircuitBreakerRegistry breakers) {
        this(properties, objectMapper, scheduler, clock, retries.retry(RETRY), breakers.circuitBreaker(BREAKER));
    }

    /** Takes the retry and the breaker, so that tests can set them up without Spring. */
    FakeWebhookSender(PaymentProperties properties, ObjectMapper objectMapper, TaskScheduler scheduler, Clock clock,
                      Retry retry, CircuitBreaker breaker) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
        this.clock = clock;
        this.retry = retry;
        this.breaker = breaker;
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

    /**
     * Deliver {@code event}, retrying as configured.
     *
     * @return done once it is delivered, refused, or given up on (never failed: a
     *         lost delivery is logged, not thrown)
     */
    CompletableFuture<Void> deliver(WebhookEvent event) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        attempt(event, retry.asyncContext(), done);
        return done;
    }

    /** One try. A failed one schedules the next, for when the retry says, or falls back. */
    private void attempt(WebhookEvent event, Retry.AsyncContext<Void> tries, CompletableFuture<Void> done) {
        if (!breaker.tryAcquirePermission()) {
            fallback(event, "the webhook-delivery circuit breaker is open", done);
            return;
        }
        long start = breaker.getCurrentTimestamp();
        HttpRequest request;
        try {
            request = request(event);
        } catch (RuntimeException e) {
            breaker.releasePermission();
            fallback(event, "it couldn't be written: " + e.getMessage(), done);
            return;
        }
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            long took = breaker.getCurrentTimestamp() - start;
            Throwable failure = failureOf(response, error);
            if (failure == null) {
                breaker.onSuccess(took, breaker.getTimestampUnit());
                tries.onComplete();
                done.complete(null);
                return;
            }
            // The breaker counts only connection errors and 5xx (application.yml).
            breaker.onError(took, breaker.getTimestampUnit(), failure);
            long waitMillis = tries.onError(failure);
            if (waitMillis < 0) {
                fallback(event, failure.getMessage(), done);
            } else {
                scheduler.schedule(() -> attempt(event, tries, done), clock.instant().plusMillis(waitMillis));
            }
        });
    }

    /** What went wrong, if anything: a connection error, or an answer other than 2xx. */
    private static Throwable failureOf(HttpResponse<Void> response, Throwable error) {
        if (error != null) {
            return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        }
        int status = response.statusCode();
        if (status / 100 == 2) {
            return null;
        }
        return status >= 500 ? new WebhookDeliveryException(status) : new WebhookRefusedException(status);
    }

    /** The fallback: a lost webhook is logged, and the jobs settle what it would have. */
    private void fallback(WebhookEvent event, String why, CompletableFuture<Void> done) {
        log.warn("Webhook {} ({}) not delivered ({}): reconciliation and the refund job will settle it",
                event.id(), event.type(), why);
        done.complete(null);
    }

    private HttpRequest request(WebhookEvent event) {
        byte[] body = objectMapper.writeValueAsBytes(event);
        return HttpRequest.newBuilder(URI.create(properties.getFake().getWebhookUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(WebhookSignature.HEADER, WebhookSignature.sign(properties.getWebhookSecret(), clock.instant(), body))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
    }

    /** The shop answered {@code 5xx}: worth another try. */
    static class WebhookDeliveryException extends IOException {

        private static final long serialVersionUID = 1L;

        WebhookDeliveryException(int status) {
            super("the shop answered " + status);
        }
    }

    /**
     * The shop refused it ({@code 4xx}, such as a wrong secret): another try would be
     * refused too. Not an {@link IOException}, so neither the retry nor the breaker
     * counts it as a connection problem.
     */
    static class WebhookRefusedException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        WebhookRefusedException(int status) {
            super("the shop refused it with " + status);
        }
    }
}
