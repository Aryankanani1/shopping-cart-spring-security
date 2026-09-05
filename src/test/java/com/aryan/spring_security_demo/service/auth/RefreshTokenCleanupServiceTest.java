package com.aryan.spring_security_demo.service.auth;

import com.aryan.spring_security_demo.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for the scheduled purge. No Spring context, no database — the
 * repository is mocked, so this isolates the one thing the service is
 * responsible for: asking the repository to delete tokens expired as of "now".
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @InjectMocks private RefreshTokenCleanupService cleanupService;

    @Test
    void purgeExpiredTokens_deletesUsingCurrentInstantAsCutoff() {
        // Arrange
        Instant before = Instant.now();
        when(refreshTokenRepository.deleteAllExpiredBefore(any())).thenReturn(3);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);

        // Act
        cleanupService.purgeExpiredTokens();

        // Assert — the cutoff handed to the repository is "now" (within this test window)
        verify(refreshTokenRepository).deleteAllExpiredBefore(cutoff.capture());
        assertThat(cutoff.getValue())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(Instant.now().plus(1, ChronoUnit.SECONDS));
    }
}
