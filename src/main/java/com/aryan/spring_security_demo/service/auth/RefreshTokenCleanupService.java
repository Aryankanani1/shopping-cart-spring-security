package com.aryan.spring_security_demo.service.auth;

import com.aryan.spring_security_demo.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Periodically sweeps expired refresh tokens out of the database. Every refresh
 * rotates the token — revoking the old row and inserting a new one — so the
 * {@code refresh_tokens} table would otherwise grow without bound. Expired rows
 * carry no value (they can neither be rotated nor detected as reuse once past
 * their expiry), so they are safe to delete outright.
 *
 * <p>This is the scheduling <em>critical section</em>: it mutates the database
 * (a bulk delete inside a transaction) on security-sensitive rows, so it must
 * never run more than once at a time. It runs on a <strong>cron</strong> schedule
 * ({@code auth.token.cleanup-cron}, default daily at 03:00 — an off-peak window),
 * and {@link SchedulerLock @SchedulerLock} guarantees single execution: in a
 * multi-instance deployment every node fires the cron, but only the one that
 * acquires the shared {@code shedlock} row runs the purge; the rest skip it.
 * {@code lockAtMostFor} releases the lock if the holder dies mid-run, and
 * {@code lockAtLeastFor} guards against a second run under clock skew. Scheduling
 * is enabled by {@code SchedulingConfig}, which also enables ShedLock.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    @Scheduled(cron = "${auth.token.cleanup-cron}")
    @SchedulerLock(name = "purgeExpiredTokens", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteAllExpiredBefore(clock.instant());
        if (deleted > 0) {
            log.info("Purged {} expired refresh token(s)", deleted);
        }
    }
}
