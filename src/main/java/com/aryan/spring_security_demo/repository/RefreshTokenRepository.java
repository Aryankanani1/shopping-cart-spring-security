package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
