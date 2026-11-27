package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.InvalidOrderStateException;
import com.aryan.spring_security_demo.order.OrderProperties;
import com.aryan.spring_security_demo.order.OrderServiceInterface;
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

/** The checkout saga's deadline: which orders it asks for, and that one it can't cancel doesn't stop the rest. */
class CheckoutDeadlineJobTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    private final CheckoutSaga checkoutSaga = mock(CheckoutSaga.class);
    private final OrderServiceInterface orderService = mock(OrderServiceInterface.class);
    private final OrderProperties properties = new OrderProperties();
    private final CheckoutDeadlineJob job =
            new CheckoutDeadlineJob(checkoutSaga, orderService, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void run_compensatesCheckoutsUnpaidForLongerThanTheWindow() {
        properties.setUnpaidExpiry(Duration.ofMinutes(30));
        Instant cutoff = NOW.minus(Duration.ofMinutes(30));
        when(orderService.unpaidOrdersPlacedBefore(cutoff)).thenReturn(List.of(1L));

        job.run();

        verify(checkoutSaga).expire(1L, cutoff);
    }

    @Test
    void expireUnpaidOrders_keepsGoingPastOrdersItCantCancelNow() {
        when(orderService.unpaidOrdersPlacedBefore(NOW)).thenReturn(List.of(1L, 2L, 3L, 4L));
        // Being paid for: the saga refuses (a 409 conflict).
        when(checkoutSaga.expire(1L, NOW)).thenThrow(new InvalidOrderStateException("payment in progress"));
        when(checkoutSaga.expire(2L, NOW)).thenThrow(new IllegalStateException("database hiccup"));
        when(checkoutSaga.expire(3L, NOW)).thenReturn(true);
        when(checkoutSaga.expire(4L, NOW)).thenReturn(false);  // paid since the scan

        assertThat(job.expireUnpaidOrders(NOW)).isEqualTo(1);
    }
}
