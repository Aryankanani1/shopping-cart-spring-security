package com.aryan.spring_security_demo.common.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The outbox against MySQL: what only the database shows. A message commits or
 * rolls back with the transaction that wrote it, two relays never claim one
 * message ({@code SKIP LOCKED}), and a claim that runs out makes the message due
 * again. The messages are about refunds that don't exist, which the refund handler
 * treats as refunded already, so no provider is involved.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class OutboxIntegrationTest {

    private static final String TYPE = "payment.refund";
    private static final Long NO_SUCH_REFUND = 999_999_999L;

    @Autowired private Outbox outbox;
    @Autowired private OutboxStore store;
    @Autowired private OutboxRelay relay;
    @Autowired private OutboxMessageRepository repository;
    @Autowired private OutboxProperties properties;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    @BeforeEach
    void setUp() {
        repository.deleteAll();  // other classes' messages would be claimed too
    }

    @Test
    @DisplayName("a message written in a transaction that rolls back is never owed")
    void aMessage_isOwedOnlyIfItsTransactionCommits() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            outbox.append(TYPE, NO_SUCH_REFUND);
            status.setRollbackOnly();
        });
        assertThat(repository.count()).isZero();

        transaction.executeWithoutResult(status -> outbox.append(TYPE, NO_SUCH_REFUND));

        // Delivered right after the commit, on a background thread.
        await().atMost(Duration.ofSeconds(10)).until(() -> repository.findAll().stream()
                .allMatch(message -> message.getProcessedAt() != null));
        assertThat(repository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getAttempts()).isEqualTo(1);
            assertThat(message.getLastError()).isNull();
        });
    }

    @Test
    @DisplayName("two relays at once never claim the same message, and neither waits for the other")
    void twoRelays_neverClaimTheSameMessage() throws Exception {
        Instant now = clock.instant();
        Long first = repository.save(new OutboxMessage(TYPE, NO_SUCH_REFUND, now.minusSeconds(2))).getId();
        Long second = repository.save(new OutboxMessage(TYPE, NO_SUCH_REFUND, now.minusSeconds(1))).getId();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // Another relay, part-way through claiming the oldest message: its row lock is held.
        CompletableFuture<List<Long>> other = CompletableFuture.supplyAsync(() ->
                new TransactionTemplate(transactionManager).execute(status -> {
                    List<Long> ids = repository.lockDue(now, 1).stream().map(OutboxMessage::getId).toList();
                    locked.countDown();
                    waitFor(release);
                    return ids;
                }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        // Answers while the other still holds its lock: the locked row is passed over, not waited for.
        List<Long> claimed = CompletableFuture.supplyAsync(() -> store.claimDue(now, 10)).get(10, TimeUnit.SECONDS)
                .stream().map(OutboxStore.Claimed::id).toList();
        release.countDown();

        assertThat(other.get(10, TimeUnit.SECONDS)).containsExactly(first);
        assertThat(claimed).containsExactly(second);
    }

    @Test
    @DisplayName("a claimed message is no one else's until its claim runs out, then it is due again")
    void aClaimedMessage_isDueAgainOnceItsClaimRunsOut() {
        Instant now = clock.instant();
        Long id = repository.save(new OutboxMessage(TYPE, NO_SUCH_REFUND, now)).getId();

        assertThat(store.claimDue(now, 10)).extracting(OutboxStore.Claimed::id).containsExactly(id);
        // The relay that claimed it stopped before recording anything.
        assertThat(store.claimDue(now, 10)).isEmpty();
        assertThat(store.claim(List.of(id))).isEmpty();

        Instant claimRanOut = now.plus(properties.getClaimTimeout()).plusSeconds(1);
        assertThat(relay.deliverDue(claimRanOut)).isEqualTo(1);

        OutboxMessage message = repository.findById(id).orElseThrow();
        assertThat(message.getAttempts()).isEqualTo(2);
        assertThat(message.getProcessedAt()).isNotNull();
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
