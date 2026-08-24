package com.aryan.spring_security_demo.bootstrap;

import com.aryan.spring_security_demo.config.StartupProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;

/**
 * Boot-time health gate for the things this service depends on:
 * <ul>
 *   <li>the database (a live connection is validated), and</li>
 *   <li>any external HTTP APIs listed in
 *       {@code app.startup.connectivity.endpoints} (comma-separated).</li>
 * </ul>
 *
 * <p>External checks are best-effort and log a WARN on failure rather than
 * aborting startup — a payment/shipping provider being briefly unreachable
 * shouldn't stop the app from booting. A failed <b>database</b> check is logged
 * as an ERROR because the app cannot function without it.
 *
 * <p>Uses the JDK {@link HttpClient}, so no HTTP-client dependency is added.
 */
@Component
@Order(30)
@RequiredArgsConstructor
@Slf4j
public class ConnectivityCheckRunner implements ApplicationRunner {

    private final DataSource dataSource;
    private final StartupProperties startupProperties;

    @Override
    public void run(ApplicationArguments args) {
        checkDatabase();
        checkExternalEndpoints();
    }

    private long timeoutMs() {
        return startupProperties.getConnectivity().getTimeoutMs();
    }

    private void checkDatabase() {
        int validationTimeoutSeconds = (int) Math.max(1, timeoutMs() / 1000);
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(validationTimeoutSeconds);
            if (valid) {
                log.info("[health] Database   : OK ({})", connection.getMetaData().getURL());
            } else {
                log.error("[health] Database   : connection obtained but reported INVALID");
            }
        } catch (Exception ex) {
            log.error("[health] Database   : UNREACHABLE — {}", ex.getMessage());
        }
    }

    private void checkExternalEndpoints() {
        List<String> endpoints = startupProperties.getConnectivity().getEndpoints().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (endpoints.isEmpty()) {
            log.info("[health] External   : no endpoints configured (skipping)");
            return;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs()))
                .build();

        for (String endpoint : endpoints) {
            ping(client, endpoint);
        }
    }

    private void ping(HttpClient client, String endpoint) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs()))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status < 400) {
                log.info("[health] External   : OK  {} -> HTTP {}", endpoint, status);
            } else {
                log.warn("[health] External   : DEGRADED {} -> HTTP {}", endpoint, status);
            }
        } catch (Exception ex) {
            log.warn("[health] External   : UNREACHABLE {} — {}", endpoint, ex.getMessage());
        }
    }
}
