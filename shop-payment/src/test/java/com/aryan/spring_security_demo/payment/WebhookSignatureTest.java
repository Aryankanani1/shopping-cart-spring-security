package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Only a request signed with the secret, over exactly this body, recently, gets through. */
class WebhookSignatureTest {

    private static final String SECRET = "test-only-webhook-secret-0000000000000000000000";
    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");
    private static final Duration TOLERANCE = Duration.ofMinutes(5);
    private static final byte[] BODY = "{\"id\":\"evt_1\",\"type\":\"charge.succeeded\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void aFreshSignature_overThisBody_isAccepted() {
        String header = WebhookSignature.sign(SECRET, NOW, BODY);

        assertThatCode(() -> WebhookSignature.verify(SECRET, header, BODY, NOW.plusSeconds(30), TOLERANCE))
                .doesNotThrowAnyException();
    }

    @Test
    void aChangedBody_isRefused() {
        String header = WebhookSignature.sign(SECRET, NOW, BODY);
        byte[] tampered = "{\"id\":\"evt_1\",\"type\":\"charge.failed\"}".getBytes(StandardCharsets.UTF_8);

        assertRefused(header, tampered, NOW);
    }

    @Test
    void anotherSecret_isRefused() {
        String header = WebhookSignature.sign("another-secret-of-thirty-two-characters!", NOW, BODY);

        assertRefused(header, BODY, NOW);
    }

    // Replay protection: the timestamp is signed, so an old capture can't be reused.
    @Test
    void aSignatureOlderThanTheTolerance_isRefused() {
        String header = WebhookSignature.sign(SECRET, NOW, BODY);

        assertRefused(header, BODY, NOW.plus(TOLERANCE).plusSeconds(1));
    }

    @Test
    void aSignatureFromTooFarAhead_isRefused() {
        String header = WebhookSignature.sign(SECRET, NOW.plus(TOLERANCE).plusSeconds(1), BODY);

        assertRefused(header, BODY, NOW);
    }

    @Test
    void aChangedTimestamp_breaksTheSignature() {
        String header = WebhookSignature.sign(SECRET, NOW.minus(Duration.ofHours(1)), BODY)
                .replaceFirst("t=\\d+", "t=" + NOW.getEpochSecond());

        assertRefused(header, BODY, NOW);
    }

    @Test
    void aMissingOrMalformedHeader_isRefused() {
        for (String header : new String[] {null, "", "v1=abc", "t=123", "t=soon,v1=abc", "garbage"}) {
            assertRefused(header, BODY, NOW);
        }
    }

    private static void assertRefused(String header, byte[] body, Instant now) {
        assertThatThrownBy(() -> WebhookSignature.verify(SECRET, header, body, now, TOLERANCE))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }
}
