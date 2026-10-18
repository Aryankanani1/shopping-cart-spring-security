package com.aryan.spring_security_demo.common.bootstrap;

import com.aryan.spring_security_demo.common.config.StartupProperties;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * The startup check of an external endpoint: tried again while it can't be reached
 * or answers 5xx (docs/payment-design.md, section 10), then a warning, never a
 * failed startup. Against a real HTTP server on a free local port.
 */
class ConnectivityCheckRunnerTest {

    private final AtomicInteger requests = new AtomicInteger();
    /** What the server answers, in turn; 200 once they run out. */
    private final Queue<Integer> statuses = new ConcurrentLinkedQueue<>();
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpServer server;
    private ConnectivityCheckRunner runner;
    private String endpoint;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ping", exchange -> {
            requests.incrementAndGet();
            Integer status = statuses.poll();
            exchange.sendResponseHeaders(status == null ? 200 : status, -1);
            exchange.close();
        });
        server.start();
        endpoint = "http://localhost:" + server.getAddress().getPort() + "/ping";
        // application.yml's startup-check rules (the context test checks they match), 1 ms apart.
        RetryRegistry retries = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(IOException.class)
                .build());
        runner = new ConnectivityCheckRunner(mock(DataSource.class), new StartupProperties(), retries);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void anEndpointBackOnTheThirdTry_isCheckedThreeTimes() {
        statuses.addAll(List.of(503, 502, 200));

        runner.ping(client, endpoint);

        assertThat(requests).hasValue(3);
    }

    @Test
    void anEndpointThatStaysDown_isTriedThreeTimes_thenOnlyWarnedAbout() {
        statuses.addAll(List.of(503, 503, 503));

        assertThatCode(() -> runner.ping(client, endpoint)).doesNotThrowAnyException();

        assertThat(requests).hasValue(3);
    }

    // A 4xx is an answer: trying again won't change it.
    @Test
    void anEndpointThatAnswers4xx_isNotTriedAgain() {
        statuses.add(404);

        runner.ping(client, endpoint);

        assertThat(requests).hasValue(1);
    }

    @Test
    void anEndpointThatCantBeReached_isOnlyWarnedAbout() {
        assertThatCode(() -> runner.ping(client, "http://localhost:1/ping")).doesNotThrowAnyException();
    }
}
