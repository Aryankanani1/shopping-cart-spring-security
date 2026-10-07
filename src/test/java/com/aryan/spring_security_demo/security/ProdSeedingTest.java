package com.aryan.spring_security_demo.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME;

/**
 * What a production start seeds. The sample products (with generated placeholder
 * images) are for trying the app out; a real shop must not find them in its
 * catalogue. The default categories still seed everywhere.
 */
class ProdSeedingTest {

    private static final String PRODUCTS = "app.startup.seed.products-enabled";

    // Regression: products-enabled was true in the base config, so prod seeded them.
    @Test
    void prod_doesNotSeedTheSampleProducts() {
        assertThat(environment(Map.of()).getProperty(PRODUCTS, Boolean.class)).isFalse();
    }

    @Test
    void prod_stillSeedsTheDefaultCategories() {
        assertThat(environment(Map.of()).getProperty("app.startup.seed.enabled", Boolean.class)).isTrue();
    }

    @Test
    void dev_seedsTheSampleProducts() {
        assertThat(environment(Map.of("SPRING_PROFILES_ACTIVE", "dev")).getProperty(PRODUCTS, Boolean.class)).isTrue();
    }

    /** The committed config given only these environment variables (no profile set means prod). */
    private static StandardEnvironment environment(Map<String, Object> environmentVariables) {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.replace(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environmentVariables));
        sources.remove(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);

        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }
}
