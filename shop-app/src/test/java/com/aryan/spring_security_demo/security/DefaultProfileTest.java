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
 * Which profile {@code application.yml} activates. The dev profile seeds admin
 * accounts whose password is in the README, so it must only ever be chosen on
 * purpose: falling back to it gave a deploy that forgot SPRING_PROFILES_ACTIVE a
 * public admin login.
 */
class DefaultProfileTest {

    // Regression: the fallback was dev.
    @Test
    void noProfileChosen_runsAsProd() {
        assertThat(activeProfiles(Map.of())).containsExactly("prod");
    }

    @Test
    void devIsUsedWhenChosen() {
        assertThat(activeProfiles(Map.of("SPRING_PROFILES_ACTIVE", "dev"))).containsExactly("dev");
    }

    /** The profiles the committed config activates, given only these environment variables. */
    private static String[] activeProfiles(Map<String, Object> environmentVariables) {
        StandardEnvironment environment = new StandardEnvironment();
        // Replace the real environment and system properties, so a profile set in
        // the shell or on the command line can't change the result.
        MutablePropertySources sources = environment.getPropertySources();
        sources.replace(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environmentVariables));
        sources.remove(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);

        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment.getActiveProfiles();
    }
}
