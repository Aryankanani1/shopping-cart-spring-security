package com.aryan.spring_security_demo.common.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Owing a message, and the jobs and listener that deliver it. */
class OutboxTest {

    private static final Instant NOW = Instant.parse("2026-11-26T09:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void append_writesAMessageDueNow_andAsksForItsDeliveryAfterTheCommit() {
        OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        when(repository.save(any())).thenAnswer(invocation -> {
            OutboxMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 5L);
            return message;
        });

        new Outbox(repository, events, clock).append("payment.refund", 11L);

        verify(repository).save(argThat(message ->
                message.getType().equals("payment.refund") && message.getAggregateId().equals(11L)
                        && message.getAvailableAt().equals(NOW) && message.getAttempts() == 0
                        && message.getProcessedAt() == null));
        verify(events).publishEvent(new OutboxAppendedEvent(5L));
    }

    // The transaction has committed by then: a failure must not escape, there is nothing to roll back.
    @Test
    void theAfterCommitDelivery_leavesAFailureToTheJob() {
        OutboxRelay relay = mock(OutboxRelay.class);
        when(relay.deliver(List.of(5L))).thenThrow(new IllegalStateException("database hiccup"));

        assertThatCode(() -> new OutboxAfterCommitDispatcher(relay).onAppended(new OutboxAppendedEvent(5L)))
                .doesNotThrowAnyException();
    }

    @Test
    void theJob_deliversWhatIsDueNow_andDeletesWhatWasProcessedBeforeTheRetention() {
        OutboxRelay relay = mock(OutboxRelay.class);
        OutboxProperties properties = new OutboxProperties();
        OutboxRelayJob job = new OutboxRelayJob(relay, properties, clock);

        job.run();
        job.cleanUp();

        verify(relay).deliverDue(NOW);
        verify(relay).deleteProcessedBefore(NOW.minus(properties.getRetention()));
    }
}
