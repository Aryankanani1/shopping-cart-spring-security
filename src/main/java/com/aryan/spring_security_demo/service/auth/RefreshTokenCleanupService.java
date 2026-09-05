package com.aryan.spring_security_demo.service.auth;

import com.aryan.spring_security_demo.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Periodically sweeps expired refresh tokens out of the database. Every refresh
 * rotates the token — revoking the old row and inserting a new one — so the
 * {@code refresh_tokens} table would otherwise grow without bound. Expired rows
 * carry no value (they can neither be rotated nor detected as reuse once past
 * their expiry), so they are safe to delete outright.
 *
 * <p>The schedule is driven by {@code auth.token.cleanup-cron} (default daily at
 * 03:00); scheduling itself is enabled by {@code SchedulingConfig}.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupService.class);

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "${auth.token.cleanup-cron}")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteAllExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired refresh token(s)", deleted);
        }
    }
}
