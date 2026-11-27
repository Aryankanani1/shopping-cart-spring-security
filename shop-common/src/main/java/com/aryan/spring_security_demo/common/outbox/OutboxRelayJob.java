package com.aryan.spring_security_demo.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Delivers the outbox messages that are due: ones the delivery right after their
 * commit couldn't finish (a handler failed, or isn't done yet) or never started
 * (the app stopped right after the commit). Runs on {@code app.outbox.relay-cron},
 * and deletes old processed messages on {@code app.outbox.cleanup-cron}, each on
 * one instance at a time (ShedLock).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayJob {

    private final OutboxRelay relay;
    private final OutboxProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${app.outbox.relay-cron}")
    @SchedulerLock(name = "outboxRelay", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void run() {
        int processed = relay.deliverDue(clock.instant());
        if (processed > 0) {
            log.info("Outbox relay: {} message(s) processed", processed);
        }
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron}")
    @SchedulerLock(name = "outboxCleanup", lockAtMostFor = "PT5M", lockAtLeastFor = "PT20S")
    public void cleanUp() {
        int deleted = relay.deleteProcessedBefore(clock.instant().minus(properties.getRetention()));
        if (deleted > 0) {
            log.info("Outbox cleanup: {} processed message(s) deleted", deleted);
        }
    }
}
