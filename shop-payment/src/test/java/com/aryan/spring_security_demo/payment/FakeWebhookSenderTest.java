package com.aryan.spring_security_demo.payment;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The simulated provider's deliveries, against a real HTTP server on a free local
 * port that answers with the statuses a test lines up. Retries are scheduled, never
 * slept: the scheduler here runs each at once and records when it was asked to.
 */
class FakeWebhookSenderTest {

    private static final String SECRET = "test-only-webhook-secret-0000000000000000000000";
    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    private record Received(String signature, byte[] body) {
    }

    private final PaymentProperties properties = new PaymentProperties();
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final Queue<Received> received = new ConcurrentLinkedQueue<>();
    /** What the server answers, in turn; 200 once they run out. */
    private final Queue<Integer> statuses = new ConcurrentLinkedQueue<>();
    /** Opens only after 10 calls, so the retry tests see every try; the breaker test has its own. */
    private final CircuitBreaker breaker = breaker(10);
    private HttpServer server;
    private FakeWebhookSender sender;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/webhooks", exchange -> {
            received.add(new Received(exchange.getRequestHeaders().getFirst(WebhookSignature.HEADER),
                    exchange.getRequestBody().readAllBytes()));
            Integer status = statuses.poll();
            exchange.sendResponseHeaders(status == null ? 200 : status, -1);
            exchange.close();
        });
        server.start();
        properties.setWebhookSecret(SECRET);
        properties.getFake().setWebhookUrl("http://localhost:" + server.getAddress().getPort() + "/webhooks");
        sender = sender(breaker);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void deliver_postsTheEvent_signedSoTheShopCanVerifyIt() throws Exception {
        WebhookEvent event = new WebhookEvent("evt_1", WebhookEvent.CHARGE_SUCCEEDED, NOW.getEpochSecond(),
                new WebhookEvent.Data("pay_7", "ch_1", "Visa", "4242", null, null));

        sender.deliver(event).get(10, TimeUnit.SECONDS);

        assertThat(received).singleElement().satisfies(request -> {
            assertThatCode(() -> WebhookSignature.verify(SECRET, request.signature(), request.body(), NOW,
                    properties.getWebhookTolerance())).doesNotThrowAnyException();
            assertThat(new String(request.body(), StandardCharsets.UTF_8))
                    .contains("\"type\":\"charge.succeeded\"", "\"idempotencyKey\":\"pay_7\"");
        });
    }

    @Test
    void send_schedulesTheDeliveryASecondLater() {
        sender.send(WebhookEvent.CHARGE_FAILED, new WebhookEvent.Data("pay_7", null, null, null, "declined", null));

        verify(scheduler).schedule(any(Runnable.class), eq(NOW.plus(FakeWebhookSender.DELAY)));
    }

    @Test
    void send_doesNothing_untilBothTheSecretAndTheUrlAreSet() {
        properties.getFake().setWebhookUrl("");
        sender.send(WebhookEvent.CHARGE_FAILED, new WebhookEvent.Data("pay_7", null, null, null, "declined", null));
        properties.getFake().setWebhookUrl("http://localhost:1/webhooks");
        properties.setWebhookSecret("");
        sender.send(WebhookEvent.CHARGE_FAILED, new WebhookEvent.Data("pay_7", null, null, null, "declined", null));

        verifyNoInteractions(scheduler);
    }

    // The scheduler's one thread also runs the payment jobs: a retry is scheduled for
    // when the retry says (1 s, then 2 s), never waited for on that thread.
    @Test
    void aFailedDelivery_isTriedAgain_scheduledForWhenTheRetrySays() throws Exception {
        runScheduledTasksAtOnce();
        statuses.addAll(List.of(500, 503, 200));

        sender.deliver(event()).get(10, TimeUnit.SECONDS);

        assertThat(received).hasSize(3);
        ArgumentCaptor<Instant> when = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler, times(2)).schedule(any(Runnable.class), when.capture());
        assertThat(when.getAllValues()).containsExactly(NOW.plusSeconds(1), NOW.plusSeconds(2));
    }

    @Test
    void aDeliveryThatKeepsFailing_fallsBack_afterThreeTries_withoutThrowing() throws Exception {
        runScheduledTasksAtOnce();
        statuses.addAll(List.of(500, 500, 500));

        assertThatCode(() -> sender.deliver(event()).get(10, TimeUnit.SECONDS)).doesNotThrowAnyException();

        assertThat(received).hasSize(3);
    }

    // A wrong secret, say: another try would be refused too.
    @Test
    void aRefusedDelivery_isNotTriedAgain() throws Exception {
        runScheduledTasksAtOnce();
        statuses.add(401);

        sender.deliver(event()).get(10, TimeUnit.SECONDS);

        assertThat(received).hasSize(1);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void aDeliveryThatCantConnect_isTriedThreeTimes_thenLoggedNotThrown() {
        runScheduledTasksAtOnce();
        properties.getFake().setWebhookUrl("http://localhost:1/webhooks");

        assertThatCode(() -> sender.deliver(event()).get(30, TimeUnit.SECONDS)).doesNotThrowAnyException();

        verify(scheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void whileDeliveriesKeepFailing_theBreakerOpens_andTheRestAreSkipped() throws Exception {
        runScheduledTasksAtOnce();
        statuses.addAll(List.of(500, 500, 500, 500, 500, 500));
        CircuitBreaker tight = breaker(2);
        FakeWebhookSender guarded = sender(tight);

        guarded.deliver(event()).get(10, TimeUnit.SECONDS);  // 2 failures open it; its third try is skipped
        guarded.deliver(event()).get(10, TimeUnit.SECONDS);  // skipped

        assertThat(received).hasSize(2);
        assertThat(tight.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    /** application.yml's internal-call retry (the context test checks it matches), and a breaker. */
    private FakeWebhookSender sender(CircuitBreaker breaker) {
        Retry retry = Retry.of(FakeWebhookSender.RETRY, RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1), 2))
                .retryExceptions(IOException.class)
                .build());
        return new FakeWebhookSender(properties, JsonMapper.builder().build(), scheduler,
                Clock.fixed(NOW, ZoneOffset.UTC), retry, breaker);
    }

    /** application.yml's webhook-delivery rules, opening once {@code minimumCalls} calls have half failed. */
    private static CircuitBreaker breaker(int minimumCalls) {
        return CircuitBreaker.of(FakeWebhookSender.BREAKER, CircuitBreakerConfig.custom()
                .slidingWindowSize(Math.max(minimumCalls, 4))
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(50)
                .recordExceptions(IOException.class)
                .build());
    }

    private static WebhookEvent event() {
        return new WebhookEvent("evt_1", WebhookEvent.CHARGE_SUCCEEDED, NOW.getEpochSecond(), null);
    }

    private void runScheduledTasksAtOnce() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
    }
}
