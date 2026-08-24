package com.aryan.spring_security_demo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Type-safe, validated configuration for the startup pipeline
 * ({@code com.aryan.spring_security_demo.bootstrap}). Groups the {@code app.startup.*}
 * settings that were previously scattered across {@code @Value} annotations.
 *
 * <p>Defaults live here so the <em>same jar runs in every environment</em> — only
 * the externalized configuration changes. Invalid config fails fast at startup
 * via {@code @Validated} rather than surfacing as a runtime surprise.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.startup")
public class StartupProperties {

    @Valid
    private final Seed seed = new Seed();

    @Valid
    private final Connectivity connectivity = new Connectivity();

    @Valid
    private final Cache cache = new Cache();

    /** {@code app.startup.seed.*} — default catalog category seeding. */
    @Getter
    @Setter
    public static class Seed {
        /** Whether DefaultDataRunner seeds default categories into an empty catalog. */
        private boolean enabled = true;

        /** Categories to seed when missing. Comma-separated in properties. */
        @NotEmpty
        private List<String> categories = new ArrayList<>(List.of(
                "Electronics", "Books", "Clothing", "Home & Kitchen",
                "Toys", "Sports", "Beauty", "Groceries"));
    }

    /** {@code app.startup.connectivity.*} — boot-time dependency checks. */
    @Getter
    @Setter
    public static class Connectivity {
        /** Optional external HTTP endpoints to verify at boot. Empty = database only. */
        private List<String> endpoints = new ArrayList<>();

        /** Timeout applied to DB validation and each external ping, in milliseconds. */
        @Positive
        private long timeoutMs = 3000;
    }

    /** {@code app.startup.cache.*} — catalog cache warm-up. */
    @Getter
    @Setter
    public static class Cache {
        /** Whether CacheWarmupRunner preloads the catalog caches at boot. */
        private boolean warmupEnabled = true;
    }
}
