package com.aryan.spring_security_demo.order;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The expiry job: which orders it asks for, and that one it can't cancel doesn't stop the rest. */
class UnpaidOrderExpiryJobTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    private final OrderServiceInterface orderService = mock(OrderServiceInterface.class);
    private final OrderProperties properties = new OrderProperties();
    private final UnpaidOrderExpiryJob job =
            new UnpaidOrderExpiryJob(orderService, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void run_cancelsOrdersUnpaidForLongerThanTheWindow() {
        properties.setUnpaidExpiry(Duration.ofMinutes(30));
        Instant cutoff = NOW.minus(Duration.ofMinutes(30));
        when(orderService.unpaidOrdersPlacedBefore(cutoff)).thenReturn(List.of(1L));

        job.run();

        verify(orderService).expireIfUnpaid(1L, cutoff);
    }

    @Test
    void expireUnpaidOrders_keepsGoingPastOrdersItCantCancelNow() {
        when(orderService.unpaidOrdersPlacedBefore(NOW)).thenReturn(List.of(1L, 2L, 3L, 4L));
        // Being paid for: the payment module refuses (a 409 conflict).
        when(orderService.expireIfUnpaid(1L, NOW)).thenThrow(new InvalidOrderStateException("payment in progress"));
        when(orderService.expireIfUnpaid(2L, NOW)).thenThrow(new IllegalStateException("database hiccup"));
        when(orderService.expireIfUnpaid(3L, NOW)).thenReturn(true);
        when(orderService.expireIfUnpaid(4L, NOW)).thenReturn(false);  // paid since the scan

        assertThat(job.expireUnpaidOrders(NOW)).isEqualTo(1);
    }
}
