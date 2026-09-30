package com.aryan.spring_security_demo.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Fetch the owning user eagerly: rotation needs the user's roles to mint a
     * fresh access token, and this runs outside the request's normal
     * authenticated context, so we cannot rely on an open session later.
     */
    @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHash(@Param("hash") String tokenHash);

    /**
     * Revoke every still-active token for a user in one statement — used both on
     * "log out everywhere" and, defensively, when a revoked token is replayed
     * (a signal the token may have been stolen).
     */
    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllForUser(@Param("userId") Long userId);

    /**
     * Hard-delete every token a user holds — used when the password changes.
     * Deleted rather than revoked on purpose: a revoked token presented later
     * trips reuse detection, which would also kill the fresh token just issued to
     * the session that made the change. A deleted token is simply unknown (401).
     */
    @Modifying
    @Query("delete from RefreshToken t where t.user.id = :userId")
    int deleteAllForUser(@Param("userId") Long userId);

    /**
     * Bulk-delete tokens that expired before {@code cutoff}. Rotation mints a new
     * row on every refresh, so without this the table grows unbounded. Only
     * expired rows are purged: a revoked-but-unexpired token must stay put so a
     * replay of it can still be caught as reuse — once past its expiry it is
     * useless either way and safe to drop. Returns the number of rows removed.
     */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
