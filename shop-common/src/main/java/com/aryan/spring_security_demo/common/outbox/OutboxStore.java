package com.aryan.spring_security_demo.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * The database steps of delivering outbox messages, each a short transaction of
 * its own: claiming messages before their handlers run, and recording what
 * happened after. A separate bean from {@link OutboxRelay}, which runs the
 * handlers in between with no transaction open, so each step goes through
 * Spring's proxy and gets its transaction.
 *
 * <p>{@code REQUIRES_NEW}, as {@code RefundRecorder}'s later steps are: on the
 * background thread and in the job, where these run, it is the same as
 * {@code REQUIRED}; but if they were ever called straight from an
 * {@code AFTER_COMMIT} listener, {@code REQUIRED} would join the transaction that
 * had just committed, and the writes would be silently lost.
 */
@Service
@RequiredArgsConstructor
public class OutboxStore {

    private final OutboxMessageRepository repository;
    private final OutboxProperties properties;
    private final Clock clock;

    /** A message claimed for one delivery. */
    public record Claimed(Long id, String type, Long aggregateId, int attempts) {
    }

    /** Claim up to {@code limit} messages due by {@code dueBy}, oldest first. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Claimed> claimDue(Instant dueBy, int limit) {
        return claim(repository.lockDue(dueBy, limit));
    }

    /** Claim those of the given messages that are due now and not claimed by another relay. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Claimed> claim(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();  // "in ()" isn't valid SQL
        }
        return claim(repository.lockDueByIds(ids, Instant.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(Long id) {
        repository.findById(id).ifPresent(message -> message.processed(Instant.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryAt(Long id, Instant at, String error) {
        repository.findById(id).ifPresent(message -> message.retryAt(at, error));
    }

    @Transactional
    public int deleteProcessedBefore(Instant before) {
        return repository.deleteProcessedBefore(before);
    }

    /**
     * Push each locked message's next delivery past the claim timeout: until then
     * it is this relay's to deliver, and after it, if this relay never finished
     * (the app stopped), it is due again for any relay.
     */
    private List<Claimed> claim(List<OutboxMessage> locked) {
        Instant until = Instant.now(clock).plus(properties.getClaimTimeout());
        return locked.stream()
                .map(message -> {
                    message.claim(until);
                    return new Claimed(message.getId(), message.getType(), message.getAggregateId(),
                            message.getAttempts());
                })
                .toList();
    }
}
