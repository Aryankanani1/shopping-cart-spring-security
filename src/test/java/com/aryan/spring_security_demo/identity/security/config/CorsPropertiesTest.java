package com.aryan.spring_security_demo.identity.security.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Allowed CORS origins must be exact origins; anything else stops the app at startup. */
class CorsPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(Properties.class);

    @Test
    void none_starts() {
        context.withPropertyValues("app.cors.allowed-origins=")
                .run(started -> assertThat(started).hasNotFailed()
                        .getBean(CorsProperties.class).extracting(CorsProperties::getAllowedOrigins)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty());
    }

    @Test
    void exactOrigins_start() {
        context.withPropertyValues("app.cors.allowed-origins=https://shop.example.com, http://localhost:5173")
                .run(started -> assertThat(started.getBean(CorsProperties.class).getAllowedOrigins())
                        .containsExactly("https://shop.example.com", "http://localhost:5173"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "https://shop.example.com/", "https://shop.example.com/app", "shop.example.com",
            "ftp://shop.example.com"})
    void anythingButAnExactOrigin_stopsTheApp(String origin) {
        context.withPropertyValues("app.cors.allowed-origins=" + origin)
                .run(started -> assertThat(started).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("must list exact origins"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CorsProperties.class)
    static class Properties {
    }
}
