package com.aryan.spring_security_demo.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JWT signing key is checked when the app starts. A key that is too short, or
 * not Base64, used to start fine and then fail every login with a 401 "Invalid or
 * expired token", which says nothing about the real problem.
 */
class AuthTokenPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(Properties.class);

    @Test
    void a256BitBase64Key_starts() {
        context.withPropertyValues("auth.token.jwtSecret=CEN/4/BWJAn9lZR7HK2RXAN+ejVVU7p4oBUm7RWtt5Q=")
                .run(started -> assertThat(started).hasNotFailed());
    }

    // Regression: these keys passed the startup checks; every login then failed.
    @ParameterizedTest
    @ValueSource(strings = {
            "MDEyMzQ1Njc4OWFiY2RlZg==",  // valid Base64, but only 128 bits
            "secret",                      // a password, not a key
            "%%%%not-base64%%%%"})
    void anUnusableKey_stopsTheAppWithAClearMessage(String secret) {
        context.withPropertyValues("auth.token.jwtSecret=" + secret)
                .run(started -> assertThat(started).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("at least 256 bits"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthTokenProperties.class)
    static class Properties {
    }
}
