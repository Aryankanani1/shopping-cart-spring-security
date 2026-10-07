package com.aryan.spring_security_demo.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Bad first-admin settings stop the app at startup, with a message naming the env var. */
class AdminBootstrapPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(Properties.class);

    @Test
    void unset_starts() {
        context.run(started -> assertThat(started).hasNotFailed());
    }

    @Test
    void anEmailAndALongPassword_start() {
        context.withPropertyValues("app.bootstrap.admin.email=owner@example.com",
                        "app.bootstrap.admin.password=a-long-admin-password")
                .run(started -> assertThat(started).hasNotFailed());
    }

    @Test
    void aShortPassword_stopsTheApp() {
        context.withPropertyValues("app.bootstrap.admin.email=owner@example.com",
                        "app.bootstrap.admin.password=short")
                .run(started -> assertThat(started).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("ADMIN_PASSWORD must be 12-72 characters"));
    }

    @Test
    void anInvalidEmail_stopsTheApp() {
        context.withPropertyValues("app.bootstrap.admin.email=not-an-email")
                .run(started -> assertThat(started).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("ADMIN_EMAIL must be a valid email address"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AdminBootstrapProperties.class)
    static class Properties {
    }
}
