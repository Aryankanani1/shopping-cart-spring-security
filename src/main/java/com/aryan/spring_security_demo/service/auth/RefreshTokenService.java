package com.aryan.spring_security_demo.service.auth;

import com.aryan.spring_security_demo.config.AuthTokenProperties;
import com.aryan.spring_security_demo.exception.InvalidRefreshTokenException;
import com.aryan.spring_security_demo.model.RefreshToken;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.repository.RefreshTokenRepository;
import com.aryan.spring_security_demo.repository.UserRepository;
import com.aryan.spring_security_demo.security.user.UserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues, rotates and revokes the server-side refresh tokens described on
 * {@link RefreshToken}. The raw token is a 256-bit random string returned to the
 * client; only its SHA-256 hash is stored, so the DB never holds a usable token.
 *
 * <p>Refresh tokens are <em>rotating</em> and single-use: every successful
 * {@link #rotate refresh} burns the presented token and issues a new one. If a
 * token that was already rotated away (revoked) is replayed, that is a strong
 * signal it leaked, so the whole family for that user is revoked at once.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AuthTokenProperties authTokenProperties;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Result of a rotation: a principal ready to sign a new access token, plus the new raw refresh token. */
    public record RotatedToken(UserDetails principal, String rawRefreshToken) {}

    /**
     * Mint and persist a fresh refresh token for a user; returns the raw value for
     * the client. Takes only the id — {@code getReferenceById} hands back a proxy
     * so the FK is set without loading the full user row.
     */
    @Transactional
    public String issueFor(Long userId) {
        return persistNewToken(userRepository.getReferenceById(userId));
    }

    /**
     * Validate the presented refresh token and, if good, rotate it: revoke it and
     * issue a replacement. Throws {@link InvalidRefreshTokenException} (→ 401) for
     * an unknown, expired, or already-revoked token.
     */
    @Transactional
    public RotatedToken rotate(String rawToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));

        if (current.isRevoked()) {
            // A revoked token being presented again means it was rotated away yet
            // reused — treat as compromise and kill every token for this user.
            refreshTokenRepository.revokeAllForUser(current.getUser().getId());
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }
        if (current.isExpired()) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        current.setRevoked(true);
        User user = current.getUser();
        String newRaw = persistNewToken(user);

        // Build the principal here, inside the transaction, so the user's lazy
        // roles load while the session is open — the access-token minting that
        // follows in the controller needs the authorities.
        return new RotatedToken(UserDetails.buildUserDetails(user), newRaw);
    }

    /** Revoke a refresh token (logout). Idempotent: an unknown/dead token is a no-op. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> token.setRevoked(true));
    }

    private String persistNewToken(User user) {
        String raw = generateRawToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plusMillis(authTokenProperties.getRefreshExpirationInMils()));
        refreshTokenRepository.save(token);
        return raw;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32]; // 256 bits of entropy
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
