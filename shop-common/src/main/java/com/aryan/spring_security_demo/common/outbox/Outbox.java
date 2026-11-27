package com.aryan.spring_security_demo.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Where modules owe work that must follow a commit (docs/payment-design.md,
 * section 12). The message is written in the caller's transaction, so it is owed
 * if and only if that transaction commits. {@link OutboxAfterCommitDispatcher}
 * delivers it right after the commit, and {@link OutboxRelayJob} delivers what
 * that didn't finish.
 */
@Service
@RequiredArgsConstructor
public class Outbox {

    private final OutboxMessageRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Owe a message of {@code type} about the record {@code aggregateId}, in the
     * caller's transaction ({@code MANDATORY}): without one, nothing would tie the
     * message to the change that owes it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String type, Long aggregateId) {
        OutboxMessage message = repository.save(new OutboxMessage(type, aggregateId, Instant.now(clock)));
        // Delivered after the commit, and never if the transaction rolls back.
        events.publishEvent(new OutboxAppendedEvent(message.getId()));
    }
}
