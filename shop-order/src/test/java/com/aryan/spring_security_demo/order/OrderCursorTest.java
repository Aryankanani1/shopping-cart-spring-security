package com.aryan.spring_security_demo.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The opaque order-history cursor: it round-trips, and anything else a client sends is a 400. */
class OrderCursorTest {

    @Test
    void encodeThenDecode_returnsTheSamePosition() {
        OrderCursor cursor = new OrderCursor(Instant.parse("2026-03-14T12:30:45.123456Z"), 42L);

        assertThat(OrderCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void encodedCursor_isUrlSafe() {
        String token = new OrderCursor(Instant.parse("2026-03-14T12:30:45Z"), 1L).encode();

        assertThat(token).matches("[A-Za-z0-9_-]+");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingCursor_meansTheFirstSlice(String token) {
        assertThat(OrderCursor.decode(token)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not base64 !!",
            "2026-03-14T12:30:45Z",         // no separator
            "not-a-time|42",                 // bad timestamp
            "2026-03-14T12:30:45Z|forty-two", // bad id
            "2026-03-14T12:30:45Z|"          // missing id
    })
    void malformedCursor_isAClientError(String raw) {
        String token = raw.contains("!") ? raw
                : Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> OrderCursor.decode(token))
                .isInstanceOf(InvalidCursorException.class)
                .hasMessageContaining(token);
    }
}
