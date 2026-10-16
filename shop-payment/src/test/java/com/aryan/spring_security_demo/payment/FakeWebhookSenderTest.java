package com.aryan.spring_security_demo.payment;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** The simulated provider's deliveries, against a real HTTP server on a free local port. */
class FakeWebhookSenderTest {

    private static final String SECRET = "test-only-webhook-secret-0000000000000000000000";
    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    private final PaymentProperties properties = new PaymentProperties();
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private FakeWebhookSender sender;

    private record Received(String signature, byte[] body) {
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/webhooks", exchange -> {
            received.add(new Received(exchange.getRequestHeaders().getFirst(WebhookSignature.HEADER),
                    exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        properties.setWebhookSecret(SECRET);
        properties.getFake().setWebhookUrl("http://localhost:" + server.getAddress().getPort() + "/webhooks");
        sender = new FakeWebhookSender(properties, JsonMapper.builder().build(), scheduler, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void deliver_postsTheEvent_signedSoTheShopCanVerifyIt() {
        WebhookEvent event = new WebhookEvent("evt_1", WebhookEvent.CHARGE_SUCCEEDED, NOW.getEpochSecond(),
                new WebhookEvent.Data("pay_7", "ch_1", "Visa", "4242", null, null));

        sender.deliver(event);

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

    // A real provider would retry; the fake tries once, and reconciliation covers the rest.
    @Test
    void aDeliveryThatCantConnect_isLoggedNotThrown() {
        properties.getFake().setWebhookUrl("http://localhost:1/webhooks");

        assertThatCode(() -> sender.deliver(new WebhookEvent("evt_1", WebhookEvent.CHARGE_SUCCEEDED, 0, null)))
                .doesNotThrowAnyException();
    }
}
