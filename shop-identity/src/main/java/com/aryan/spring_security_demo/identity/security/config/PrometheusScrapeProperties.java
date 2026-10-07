package com.aryan.spring_security_demo.identity.security.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;

/**
 * The account Prometheus uses to scrape {@code /actuator/prometheus}
 * ({@code app.metrics.prometheus.*}). See {@link PrometheusScrapeSecurityConfig}.
 * Turning scraping on without a usable password fails startup instead of leaving
 * the endpoint behind a weak or empty password.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.metrics.prometheus")
public class PrometheusScrapeProperties {

    private static final int MIN_PASSWORD_LENGTH = 16;
    /** BCrypt hashes only this many bytes; the encoder throws on longer input. */
    private static final int MAX_PASSWORD_BYTES = 72;

    /** Whether Prometheus may scrape with the account below. */
    private boolean scrapeEnabled = false;

    @NotBlank
    private String username = "prometheus";

    /** From PROMETHEUS_SCRAPE_PASSWORD; never committed. */
    private String password = "";

    @AssertTrue(message = "PROMETHEUS_SCRAPE_PASSWORD must be 16-72 characters when Prometheus scraping is enabled")
    public boolean isPasswordUsableWhenEnabled() {
        if (!scrapeEnabled) {
            return true;
        }
        return password != null
                && password.length() >= MIN_PASSWORD_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }
}
