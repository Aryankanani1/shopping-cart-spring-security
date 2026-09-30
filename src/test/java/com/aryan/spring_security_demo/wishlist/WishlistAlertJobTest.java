package com.aryan.spring_security_demo.wishlist;

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
 * Unit test for the scheduled job's orchestration: every phase runs with the
 * clock's instant, and one phase failing doesn't stop the others. The service is
 * mocked; its behaviour is covered by {@link WishlistAlertServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class WishlistAlertJobTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");

    @Mock private WishlistAlertService alertService;

    @Test
    void run_failingPhase_doesNotStopTheOthers() {
        WishlistAlertJob job = new WishlistAlertJob(alertService, Clock.fixed(NOW, ZoneOffset.UTC));
        when(alertService.fireDueReminders(NOW)).thenThrow(new IllegalStateException("boom"));

        job.run();

        verify(alertService).fireDueReminders(NOW);
        verify(alertService).announcePriceDrops(NOW);
        verify(alertService).announceRestocks(NOW);
    }
}
