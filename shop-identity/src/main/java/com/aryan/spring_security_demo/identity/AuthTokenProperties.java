package com.aryan.spring_security_demo.identity;

import com.aryan.spring_security_demo.identity.security.jwt.RsaKeys;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Type-safe, validated token settings ({@code auth.token.*}).
 *
 * <p>Access tokens are signed with an RSA private key that only this application
 * holds, and checked with its public key, which is published (JwksController) so
 * another service can check them too without being able to make one. The key has
 * no default: it comes from the {@code JWT_SIGNING_KEY} environment variable. A
 * missing or unusable key fails the application at startup instead of at the
 * first login. Only the dev and test profiles may do without one, and then a
 * throwaway key is made at startup ({@code generateKeyIfMissing}).
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "auth.token")
public class AuthTokenProperties {

    static final String KEY_COMMAND =
            "openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 | tr -d '\\n'";

    /**
     * The RSA private key that signs access tokens: PKCS#8, Base64 on one line
     * (or PEM), at least 2048 bits. Its public half is derived from it.
     */
    private String signingKey;

    /**
     * Public keys that still verify tokens but no longer sign them (X.509, Base64
     * or PEM): the keys before the current one, kept for one access-token lifetime
     * after a new signing key is put in, so the tokens already out stay valid.
     */
    private List<String> previousPublicKeys = new ArrayList<>();

    /**
     * Make a throwaway signing key at startup when none is set. For the dev and
     * test profiles only: tokens stop working when the application restarts, and
     * every instance would have its own key.
     */
    private boolean generateKeyIfMissing = false;

    /** The {@code iss} claim of every token, checked on every token presented. */
    @NotBlank
    private String issuer = "shopping-cart-api";

    @AssertTrue(message = "JWT_SIGNING_KEY must be set: an RSA private key of at least 2048 bits, "
            + "in Base64 (generate one with: " + KEY_COMMAND + ")")
    public boolean isSigningKeyPresent() {
        return generateKeyIfMissing || (signingKey != null && !signingKey.isBlank());
    }

    /**
     * Parses the keys the same way JwtKeys does, so keys that pass here sign and
     * verify tokens. Without this check, a bad key would only fail at the first login.
     */
    @AssertTrue(message = "JWT_SIGNING_KEY must be an RSA private key (PKCS#8; a PKCS#1 key, as genrsa writes, "
            + "converts with openssl pkcs8 -topk8 -nocrypt) of at least 2048 bits, and each of "
            + "JWT_PREVIOUS_PUBLIC_KEYS an RSA public key (X.509) of at least 2048 bits, all in Base64 "
            + "(generate a signing key with: " + KEY_COMMAND + ")")
    public boolean isKeysUsable() {
        try {
            if (signingKey != null && !signingKey.isBlank()) {
                RsaKeys.parsePrivateKey(signingKey);
            }
            previousPublicKeys.stream().filter(key -> !key.isBlank()).forEach(RsaKeys::parsePublicKey);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Access-token lifetime in milliseconds. Kept short (default 15 min) so a
     * leaked token's exposure window is small; clients keep sessions alive by
     * exchanging a refresh token rather than by holding a long-lived access token.
     */
    @Positive
    private long expirationInMils = 900_000L;

    /**
     * Refresh-token lifetime in milliseconds (default 7 days). Longer-lived than
     * the access token but revocable server-side (see {@code RefreshTokenService}),
     * so control is retained despite the length.
     */
    @Positive
    private long refreshExpirationInMils = 604_800_000L;

    /**
     * Cron expression for the scheduled purge of expired refresh tokens (see
     * {@code RefreshTokenCleanupService}). Rotation leaves a spent row behind on
     * every refresh, so they are swept at a fixed wall-clock time (default daily
     * at 03:00, an off-peak window). Overlap across instances is prevented by
     * ShedLock, not by the schedule itself. Default: {@code 0 0 3 * * *}.
     */
    @NotBlank
    private String cleanupCron = "0 0 3 * * *";
}
