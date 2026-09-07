package com.aryan.spring_security_demo.service.auth;

import com.aryan.spring_security_demo.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the scheduled purge. The repository is mocked (a true external
 * collaborator to this class), but time is supplied by a real {@link Clock#fixed}
 * rather than a mock of our own code — so the cutoff handed to the repository is
 * exact and deterministic, not "roughly now". The service's whole contract is
 * "delete tokens expired as of the clock's instant", so verifying that call with
 * the fixed instant *is* asserting behaviour.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T03:00:00Z");

    @Mock private RefreshTokenRepository refreshTokenRepository;

    @Test
    void purgeExpiredTokens_deletesUsingTheClockInstantAsCutoff() {
        // Arrange — a fixed clock makes "now" a controlled input, not a live read
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        RefreshTokenCleanupService cleanupService =
                new RefreshTokenCleanupService(refreshTokenRepository, fixedClock);
        when(refreshTokenRepository.deleteAllExpiredBefore(NOW)).thenReturn(3);

        // Act
        cleanupService.purgeExpiredTokens();

        // Assert — cutoff is exactly the clock's instant (no time fudge factor)
        verify(refreshTokenRepository).deleteAllExpiredBefore(NOW);
    }
}
