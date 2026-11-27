package com.aryan.spring_security_demo.common.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Handing claimed messages to their handlers, and recording what happened, with the store mocked. */
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-26T09:00:00Z");

    private final OutboxStore store = mock(OutboxStore.class);
    private final OutboxProperties properties = new OutboxProperties();
    private final OutboxHandler refunds = mock(OutboxHandler.class);
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        when(refunds.type()).thenReturn("payment.refund");
        relay = new OutboxRelay(store, properties, Clock.fixed(NOW, ZoneOffset.UTC), List.of(refunds));
    }

    @Test
    void deliverDue_handsEachClaimedMessageToItsHandler_andMarksTheDoneOnesProcessed() {
        when(store.claimDue(NOW, properties.getBatchSize())).thenReturn(List.of(claimed(1L, 11L, 1), claimed(2L, 12L, 1)));
        when(refunds.handle(11L)).thenReturn(true);
        when(refunds.handle(12L)).thenReturn(true);

        assertThat(relay.deliverDue(NOW)).isEqualTo(2);

        verify(store).markProcessed(1L);
        verify(store).markProcessed(2L);
    }

    @Test
    void deliver_claimsOnlyTheGivenMessages() {
        when(store.claim(List.of(1L))).thenReturn(List.of(claimed(1L, 11L, 1)));
        when(refunds.handle(11L)).thenReturn(true);

        assertThat(relay.deliver(List.of(1L))).isEqualTo(1);

        verify(store).markProcessed(1L);
    }

    // The provider still unavailable after its retries: not an error, just not done.
    @Test
    void aHandlerNotDoneYet_isRetriedAfterTheBackoff_withNoError() {
        when(store.claimDue(NOW, properties.getBatchSize())).thenReturn(List.of(claimed(1L, 11L, 1)));
        when(refunds.handle(11L)).thenReturn(false);

        assertThat(relay.deliverDue(NOW)).isZero();

        verify(store).retryAt(1L, NOW.plusSeconds(10), null);
        verify(store, never()).markProcessed(anyLong());
    }

    @Test
    void aHandlerThatThrows_isRetriedWithTheError_andTheNextMessageStillGoes() {
        when(store.claimDue(NOW, properties.getBatchSize())).thenReturn(List.of(claimed(1L, 11L, 3), claimed(2L, 12L, 1)));
        when(refunds.handle(11L)).thenThrow(new IllegalStateException("database hiccup"));
        when(refunds.handle(12L)).thenReturn(true);

        assertThat(relay.deliverDue(NOW)).isEqualTo(1);

        verify(store).retryAt(1L, NOW.plusSeconds(40), "java.lang.IllegalStateException: database hiccup");
        verify(store).markProcessed(2L);
    }

    // A message from a newer release, say: kept until a handler for it is deployed.
    @Test
    void aMessageNoHandlerTakes_isKept_andRetriedLater() {
        when(store.claimDue(NOW, properties.getBatchSize())).thenReturn(List.of(new OutboxStore.Claimed(1L, "unknown", 11L, 1)));

        assertThat(relay.deliverDue(NOW)).isZero();

        verify(store).retryAt(1L, NOW.plusSeconds(10), "No handler for type unknown");
    }

    // The claim runs out, and the message is delivered again: the handler must be safe to repeat.
    @Test
    void failingToRecordTheResult_doesNotStopTheOtherMessages() {
        when(store.claimDue(NOW, properties.getBatchSize())).thenReturn(List.of(claimed(1L, 11L, 1), claimed(2L, 12L, 1)));
        when(refunds.handle(anyLong())).thenReturn(true);
        doThrow(new IllegalStateException("database down")).when(store).markProcessed(1L);

        assertThat(relay.deliverDue(NOW)).isEqualTo(1);

        verify(store).markProcessed(2L);
    }

    @Test
    void theBackoffDoublesAfterEachTry_upToTheMaximum() {
        assertThat(relay.backoff(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(relay.backoff(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(relay.backoff(3)).isEqualTo(Duration.ofSeconds(40));
        assertThat(relay.backoff(7)).isEqualTo(Duration.ofMinutes(10));
        assertThat(relay.backoff(1_000)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void twoHandlersForOneType_areRefusedAtStartup() {
        OutboxHandler another = mock(OutboxHandler.class);
        when(another.type()).thenReturn("payment.refund");
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        List<OutboxHandler> handlers = List.of(refunds, another);

        assertThatThrownBy(() -> new OutboxRelay(store, properties, clock, handlers))
                .isInstanceOf(IllegalStateException.class);
    }

    private static OutboxStore.Claimed claimed(Long id, Long aggregateId, int attempts) {
        return new OutboxStore.Claimed(id, "payment.refund", aggregateId, attempts);
    }
}
