package com.aryan.spring_security_demo.order;

import com.aryan.spring_security_demo.common.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Cancels orders left unpaid for longer than {@code app.orders.unpaid-expiry}, so
 * the stock checkout reserved for them goes back on sale. Runs on
 * {@code app.orders.unpaid-expiry-cron}, on one instance at a time (ShedLock).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnpaidOrderExpiryJob {

    private final OrderServiceInterface orderService;
    private final OrderProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${app.orders.unpaid-expiry-cron}")
    @SchedulerLock(name = "unpaidOrderExpiry", lockAtMostFor = "PT5M", lockAtLeastFor = "PT20S")
    public void run() {
        int expired = expireUnpaidOrders(clock.instant().minus(properties.getUnpaidExpiry()));
        if (expired > 0) {
            log.info("Unpaid order expiry: cancelled {} order(s)", expired);
        }
    }

    /**
     * Cancel every order still unpaid that was placed before {@code placedBefore},
     * each in a transaction of its own ({@link OrderServiceInterface#expireIfUnpaid}
     * on another bean, so through Spring's proxy): one that can't be cancelled now
     * is rolled back alone, and the others go on.
     *
     * @return how many were cancelled
     */
    public int expireUnpaidOrders(Instant placedBefore) {
        int expired = 0;
        for (Long orderId : orderService.unpaidOrdersPlacedBefore(placedBefore)) {
            try {
                if (orderService.expireIfUnpaid(orderId, placedBefore)) {
                    expired++;
                }
            } catch (ConflictException e) {
                // Expected now and then: a payment for it is in progress (it may still
                // be charged), so it is left until that payment is settled.
                log.info("Unpaid order {} not cancelled yet: {}", orderId, e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Unpaid order {} not cancelled, will try again", orderId, e);
            }
        }
        return expired;
    }
}
