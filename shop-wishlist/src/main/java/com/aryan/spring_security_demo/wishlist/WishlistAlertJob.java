package com.aryan.spring_security_demo.wishlist;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.function.IntSupplier;

/**
 * Runs the wishlist reminder and alert scan ({@link WishlistAlertService}) on a
 * cron schedule — {@code app.wishlist.alert-cron}, every minute by default, so a
 * reminder fires within a minute of its time.
 *
 * <p>Like the refresh-token purge, this is a scheduling <em>critical section</em>:
 * it writes notifications, so two instances running it at once could announce
 * the same event twice. {@link SchedulerLock @SchedulerLock} makes only one
 * instance run each tick; {@code lockAtLeastFor} covers clock skew between nodes.
 *
 * <p>Each phase runs in its own transaction, and a failure in one (for example an
 * optimistic-lock conflict with a user editing their wishlist) is logged and
 * retried next tick without holding up the other phases.
 */
@Component
@RequiredArgsConstructor
public class WishlistAlertJob {

    private static final Logger log = LoggerFactory.getLogger(WishlistAlertJob.class);

    private final WishlistAlertService alertService;
    private final Clock clock;

    @Scheduled(cron = "${app.wishlist.alert-cron}")
    @SchedulerLock(name = "wishlistAlerts", lockAtMostFor = "PT5M", lockAtLeastFor = "PT20S")
    public void run() {
        Instant now = clock.instant();
        runPhase("reminder", () -> alertService.fireDueReminders(now));
        runPhase("price-drop", () -> alertService.announcePriceDrops(now));
        runPhase("back-in-stock", () -> alertService.announceRestocks(now));
    }

    private void runPhase(String name, IntSupplier phase) {
        try {
            int count = phase.getAsInt();
            if (count > 0) {
                log.info("Wishlist scan: sent {} {} notification(s)", count, name);
            }
        } catch (RuntimeException e) {
            log.warn("Wishlist scan: {} phase failed, will retry next run", name, e);
        }
    }
}
