package com.aryan.spring_security_demo.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Signing and checking webhooks, in the shape real providers use:
 *
 * <pre>
 *   Payment-Signature: t=1760620800,v1=5257a869e7ec...
 * </pre>
 *
 * where {@code v1} is the hex HMAC-SHA256 of {@code "<t>.<raw body>"} under the
 * shared secret. The timestamp is inside the signature, so it can't be changed to
 * replay an old request; {@link #verify} refuses one too far from now. Stripe signs
 * the same way, in a {@code Stripe-Signature} header ({@link StripeWebhookReader}).
 */
public final class WebhookSignature {

    public static final String HEADER = "Payment-Signature";

    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSignature() {
    }

    /** The header value that signs {@code body}, sent at {@code timestamp}. */
    public static String sign(String secret, Instant timestamp, byte[] body) {
        long seconds = timestamp.getEpochSecond();
        return "t=" + seconds + ",v1=" + hmac(secret, seconds, body);
    }

    /**
     * Check that {@code header} signs {@code body} with {@code secret}, at a time
     * within {@code tolerance} of {@code now}.
     *
     * @throws InvalidWebhookSignatureException if it is missing, malformed, too old
     *         (or too far ahead), or doesn't match
     */
    public static void verify(String secret, String header, byte[] body, Instant now, Duration tolerance) {
        if (header == null || header.isBlank()) {
            throw new InvalidWebhookSignatureException("The webhook isn't signed");
        }
        Long timestamp = null;
        // Several while a secret is being rotated, one under each secret (Stripe does this).
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && "t".equals(pair[0])) {
                timestamp = parseSeconds(pair[1]);
            } else if (pair.length == 2 && "v1".equals(pair[0])) {
                signatures.add(pair[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            throw new InvalidWebhookSignatureException("The webhook signature is malformed");
        }
        Duration age = Duration.between(Instant.ofEpochSecond(timestamp), now).abs();
        if (age.compareTo(tolerance) > 0) {
            throw new InvalidWebhookSignatureException("The webhook's timestamp is too far from now");
        }
        byte[] expected = hmac(secret, timestamp, body).getBytes(StandardCharsets.US_ASCII);
        // Constant time, and every one compared: how long a wrong guess takes says
        // nothing about how close it was.
        boolean matches = false;
        for (String signature : signatures) {
            matches |= MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
        }
        if (!matches) {
            throw new InvalidWebhookSignatureException("The webhook signature doesn't match");
        }
    }

    private static String hmac(String secret, long seconds, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            mac.update((seconds + ".").getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    private static Long parseSeconds(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
