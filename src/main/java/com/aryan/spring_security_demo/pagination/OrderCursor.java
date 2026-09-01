package com.aryan.spring_security_demo.pagination;

import com.aryan.spring_security_demo.exception.InvalidCursorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * The keyset position for order-history pagination: the {@code (createdAt, id)} of
 * the last row a client has seen. {@code id} is the tiebreaker that makes the sort
 * a total order, so a slice boundary never splits or duplicates rows that share a
 * timestamp.
 *
 * <p>Serialized as an opaque URL-safe Base64 token so clients treat it as a handle,
 * not a queryable field — the internal shape can change without a contract break.
 */
public record OrderCursor(Instant createdAt, Long id) {

    private static final String SEP = "|";

    public String encode() {
        String raw = createdAt.toString() + SEP + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @return the decoded cursor, or {@code null} when absent (first slice). */
    public static OrderCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int sep = raw.lastIndexOf(SEP);
            return new OrderCursor(
                    Instant.parse(raw.substring(0, sep)),
                    Long.parseLong(raw.substring(sep + 1)));
        } catch (RuntimeException ex) {
            // Malformed/tampered token — a client mistake, not a server fault.
            throw new InvalidCursorException(token);
        }
    }
}
