package com.aryan.spring_security_demo.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code .env.example} is committed to a public repo and copied to {@code .env}
 * as is, so a key or password filled in there is one everybody knows. The
 * settings below have no default in docker-compose.yml on purpose and must stay
 * empty in the example, so each user generates their own.
 */
class EnvExampleTest {

    private static final Path ENV_EXAMPLE = Path.of(".env.example");

    // Regression: JWT_SECRET shipped with a working key, so anyone could sign an
    // admin token for a stack started from the example.
    @ParameterizedTest
    @ValueSource(strings = {"JWT_SECRET", "PROMETHEUS_SCRAPE_PASSWORD", "GRAFANA_ADMIN_PASSWORD"})
    void secretsAreLeftEmpty(String key) throws IOException {
        assertThat(valueOf(key)).as(key + " in .env.example").isEmpty();
    }

    /** The value of {@code key}, without a trailing {@code # comment}. */
    private static String valueOf(String key) throws IOException {
        for (String line : Files.readAllLines(ENV_EXAMPLE)) {
            if (line.startsWith(key + "=")) {
                String value = line.substring(key.length() + 1);
                int comment = value.indexOf(" #");
                return (comment >= 0 ? value.substring(0, comment) : value).trim();
            }
        }
        throw new AssertionError(key + " is missing from .env.example");
    }
}
