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
 * What the dev profile logs. Hibernate's bind logger prints every SQL parameter,
 * which includes password hashes and refresh-token hashes, so it stays off unless
 * someone turns it on while debugging.
 */
class DevLoggingTest {

    // Regression: dev set org.hibernate.orm.jdbc.bind to TRACE.
    @Test
    void devDoesNotLogBoundSqlParameters() {
        String level = devEnvironment().getProperty("logging.level.org.hibernate.orm.jdbc.bind");

        assertThat(level).as("bind logger level in dev").isNotEqualToIgnoringCase("TRACE");
    }

    /** The committed config as the dev profile sees it, ignoring the real environment. */
    private static StandardEnvironment devEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.replace(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new SystemEnvironmentPropertySource(
                SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of("SPRING_PROFILES_ACTIVE", "dev")));
        sources.remove(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);

        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }
}
