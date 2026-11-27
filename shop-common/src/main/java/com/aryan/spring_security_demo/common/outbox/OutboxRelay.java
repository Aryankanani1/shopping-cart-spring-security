package com.aryan.spring_security_demo.common.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Delivers outbox messages to their {@link OutboxHandler}s: claim them (a short
 * transaction), run each handler with no transaction open, then record the result
 * (another). A message whose handler fails, or isn't done yet, is due again after
 * a backoff that doubles with each try; one is never given up on, since what it
 * owes (a refund) must happen in the end.
 *
 * <p>Not {@code @Transactional}: a handler may call another system, and a
 * transaction around it would hold a pooled connection for as long as that takes
 * (CLAUDE.md).
 */
@Service
@Slf4j
public class OutboxRelay {

    private final OutboxStore store;
    private final OutboxProperties properties;
    private final Clock clock;
    private final Map<String, OutboxHandler> handlers;

    public OutboxRelay(OutboxStore store, OutboxProperties properties, Clock clock, List<OutboxHandler> handlers) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
        // Two handlers for one type is a wiring mistake: refuse to start.
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(OutboxHandler::type, Function.identity()));
    }

    /**
     * Deliver up to a batch of the messages due by {@code dueBy}.
     *
     * @return how many are processed now
     */
    public int deliverDue(Instant dueBy) {
        return deliver(store.claimDue(dueBy, properties.getBatchSize()));
    }

    /** Deliver the given messages, those not already processed or claimed by another relay. */
    public int deliver(Collection<Long> messageIds) {
        return deliver(store.claim(messageIds));
    }

    /** Delete the messages processed before {@code before}. */
    public int deleteProcessedBefore(Instant before) {
        return store.deleteProcessedBefore(before);
    }

    /** Each message on its own: one that fails is retried later, and the others go on. */
    private int deliver(List<OutboxStore.Claimed> claimed) {
        int processed = 0;
        for (OutboxStore.Claimed message : claimed) {
            try {
                if (deliver(message)) {
                    processed++;
                }
            } catch (RuntimeException e) {
                // Recording the result failed (the database is briefly unreachable):
                // the claim runs out, and the message is delivered again.
                log.warn("Outbox message {}: result not recorded, it will be delivered again", message.id(), e);
            }
        }
        return processed;
    }

    private boolean deliver(OutboxStore.Claimed message) {
        OutboxHandler handler = handlers.get(message.type());
        Instant retryAt = Instant.now(clock).plus(backoff(message.attempts()));
        if (handler == null) {
            // A message from a newer release, or one whose handler was removed: kept until one can take it.
            log.error("Outbox message {}: no handler for type {}", message.id(), message.type());
            store.retryAt(message.id(), retryAt, "No handler for type " + message.type());
            return false;
        }
        boolean done;
        try {
            done = handler.handle(message.aggregateId());
        } catch (RuntimeException e) {
            log.warn("Outbox message {} ({} {}) failed on try {}, retrying at {}", message.id(), message.type(),
                    message.aggregateId(), message.attempts(), retryAt, e);
            store.retryAt(message.id(), retryAt, e.toString());
            return false;
        }
        if (done) {
            store.markProcessed(message.id());
            return true;
        }
        store.retryAt(message.id(), retryAt, null);
        return false;
    }

    /** The wait after try {@code attempts}: the base backoff, doubled for each try after the first, capped. */
    Duration backoff(int attempts) {
        Duration max = properties.getMaxBackoff();
        Duration wait = properties.getBackoff();
        for (int i = 1; i < attempts && wait.compareTo(max) < 0; i++) {
            wait = wait.multipliedBy(2);
        }
        return wait.compareTo(max) < 0 ? wait : max;
    }
}
