package com.aryan.spring_security_demo.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Delivers a message as soon as the transaction that wrote it commits, so the work
 * starts at once rather than on {@link OutboxRelayJob}'s next run. Never if the
 * transaction rolls back: the message is gone with it.
 *
 * <p>{@code @Async}, on a background thread: Spring runs an {@code AFTER_COMMIT}
 * listener before it releases the transaction's connection, so a handler calling
 * another system right there would hold the request's pooled connection for as
 * long as that takes. On another thread no connection is held, and the request
 * doesn't wait for the handler.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxAfterCommitDispatcher {

    private final OutboxRelay relay;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppended(OutboxAppendedEvent event) {
        try {
            relay.deliver(List.of(event.messageId()));
        } catch (RuntimeException e) {
            // The transaction that owed it has committed, so there is nothing to roll
            // back: the message stays due, and OutboxRelayJob delivers it.
            log.warn("Outbox message {}: not delivered after the commit, the relay job will", event.messageId(), e);
        }
    }
}
