package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Stripe only in test mode: any other key stops the app at startup, with a message naming the env var. */
class PaymentPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(Properties.class);

    @Test
    void theFakeProvider_isTheDefault_andNeedsNoKey() {
        context.run(started -> {
            assertThat(started).hasNotFailed();
            assertThat(started.getBean(PaymentProperties.class).getProvider())
                    .isEqualTo(PaymentProperties.Provider.FAKE);
        });
    }

    @Test
    void stripe_withATestModeKey_starts() {
        for (String key : new String[] {"sk_test_x", "rk_test_x"}) {
            context.withPropertyValues("app.payment.provider=stripe", "app.payment.stripe.secret-key=" + key)
                    .run(started -> assertThat(started).hasNotFailed());
        }
    }

    @Test
    void stripe_withALiveKey_stopsTheApp() {
        context.withPropertyValues("app.payment.provider=stripe", "app.payment.stripe.secret-key=sk_live_x")
                .run(started -> assertThat(started).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("STRIPE_SECRET_KEY must be a Stripe test-mode key"));
    }

    @Test
    void stripe_withNoKey_stopsTheApp() {
        context.withPropertyValues("app.payment.provider=stripe")
                .run(started -> assertThat(started).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("STRIPE_SECRET_KEY must be a Stripe test-mode key"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaymentProperties.class)
    static class Properties {
    }
}
