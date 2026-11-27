package com.aryan.spring_security_demo.common.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Claiming messages and recording their delivery, with the repository mocked. */
class OutboxStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-26T09:00:00Z");
    private static final Instant WRITTEN = NOW.minusSeconds(60);

    private final OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
    private final OutboxProperties properties = new OutboxProperties();
    private final OutboxStore store = new OutboxStore(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void claimDue_countsTheTry_andKeepsOtherRelaysOffUntilTheClaimRunsOut() {
        OutboxMessage message = message(1L);
        when(repository.lockDue(NOW, 20)).thenReturn(List.of(message));

        assertThat(store.claimDue(NOW, 20)).containsExactly(new OutboxStore.Claimed(1L, "payment.refund", 11L, 1));

        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getAvailableAt()).isEqualTo(NOW.plus(properties.getClaimTimeout()));
    }

    @Test
    void claim_locksOnlyTheGivenMessages_dueNow() {
        when(repository.lockDueByIds(List.of(1L), NOW)).thenReturn(List.of(message(1L)));

        assertThat(store.claim(List.of(1L))).extracting(OutboxStore.Claimed::id).containsExactly(1L);
    }

    @Test
    void markProcessed_recordsWhen_andClearsTheLastError() {
        OutboxMessage message = message(1L);
        message.retryAt(NOW, "down");
        when(repository.findById(1L)).thenReturn(Optional.of(message));

        store.markProcessed(1L);

        assertThat(message.getProcessedAt()).isEqualTo(NOW);
        assertThat(message.getLastError()).isNull();
    }

    @Test
    void retryAt_makesTheMessageDueThen_withTheErrorCutToFit() {
        OutboxMessage message = message(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(message));

        store.retryAt(1L, NOW.plusSeconds(10), "x".repeat(300));

        assertThat(message.getAvailableAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(message.getLastError()).hasSize(OutboxMessage.MAX_ERROR_LENGTH);
    }

    // Two deliveries of one message (its claim ran out): the one that finished wins.
    @Test
    void retryAt_leavesAMessageAlreadyProcessed_processed() {
        OutboxMessage message = message(1L);
        message.processed(NOW);
        Instant availableAt = message.getAvailableAt();
        when(repository.findById(1L)).thenReturn(Optional.of(message));

        store.retryAt(1L, NOW.plusSeconds(10), "down");

        assertThat(message.getAvailableAt()).isEqualTo(availableAt);
        assertThat(message.getLastError()).isNull();
    }

    private static OutboxMessage message(Long id) {
        OutboxMessage message = new OutboxMessage("payment.refund", 11L, WRITTEN);
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }
}
