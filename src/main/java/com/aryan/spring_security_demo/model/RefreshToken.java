package com.aryan.spring_security_demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A server-side, revocable refresh token — the piece of state that buys back
 * control over otherwise-stateless JWT auth. The short-lived access token stays
 * a plain signed JWT (no per-request DB hit, so reads scale), while sessions are
 * governed here: revoking the row (logout, reuse detection, admin kill) stops
 * any new access token from being minted, and the current access token then
 * lapses within its short lifetime.
 *
 * <p>Only the SHA-256 <em>hash</em> of the token is stored, never the raw value:
 * a database leak therefore yields no usable tokens, the same reasoning that
 * makes us hash passwords.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refresh_tokens", indexes = {
        // Every refresh/logout looks the token up by its hash — make that a
        // unique, index-backed seek rather than a scan.
        @Index(name = "idx_refresh_tokens_hash", columnList = "token_hash", unique = true)
})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "refresh_tokens_seq")
    @SequenceGenerator(name = "refresh_tokens_seq", sequenceName = "refresh_tokens_seq", allocationSize = 50)
    private Long id;

    /** SHA-256 hex of the raw token handed to the client; the raw value is never persisted. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Flipped instead of deleted so a presented-but-revoked token is distinguishable (reuse detection). */
    @Column(nullable = false)
    private boolean revoked = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return !revoked && !isExpired();
    }
}
