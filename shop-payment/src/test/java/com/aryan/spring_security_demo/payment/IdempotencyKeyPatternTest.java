package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Idempotency-Key pattern, read as the spec's readers read it: a pattern in
 * OpenAPI (JSON Schema) matches anywhere in the value, so it has to be anchored to
 * describe what {@code @Pattern}, which matches the whole value, accepts.
 */
class IdempotencyKeyPatternTest {

    private final Pattern key = Pattern.compile(PaymentController.IDEMPOTENCY_KEY);

    @Test
    void aKeyTheApiAccepts_matches() {
        assertThat(key.matcher("3f2b9c1e-8d4a-4f6b-9e2a-1c5d7e9f0a2b").find()).isTrue();
        assertThat(key.matcher("a".repeat(8)).find()).isTrue();
        assertThat(key.matcher("a".repeat(64)).find()).isTrue();
    }

    @Test
    void aValueWithOnlyPartOfItAKey_doesNot() {
        assertThat(key.matcher("!!" + "a".repeat(8) + "!!").find()).isFalse();
        assertThat(key.matcher("a".repeat(65)).find()).isFalse();
        assertThat(key.matcher("key with spaces in it").find()).isFalse();
    }
}
