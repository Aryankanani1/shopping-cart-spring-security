package com.aryan.spring_security_demo.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Work that is owed because a transaction committed, written in that same
 * transaction (V18 migration): the transactional outbox. The message exists if and
 * only if the change that owes it commits, so the work can be neither lost (the
 * app stopping right after the commit) nor done for a change that rolled back.
 *
 * <p>It names the record the work is about ({@code aggregateId}) instead of copying
 * it: its {@link OutboxHandler} reads that record as it is when the message is
 * delivered, so a message can't carry stale data.
 *
 * <p>Delivered at least once, by {@link OutboxRelay}: claimed, handed to its
 * handler, then marked processed. If the app stops before the mark, the claim runs
 * out and the message is delivered again, so handlers must be safe to repeat.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "outbox_message")
public class OutboxMessage {

    static final int MAX_ERROR_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** What the message asks for, and so which handler takes it: {@code payment.refund}. */
    @Column(nullable = false, updatable = false, length = 64)
    private String type;

    /** The record it is about: a refund's id for {@code payment.refund}. */
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private Long aggregateId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** When it may next be delivered: when it was written, then later while claimed or after a failed try. */
    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    /** How many times it has been claimed for delivery. */
    @Column(nullable = false)
    private int attempts;

    @Column(name = "processed_at")
    private Instant processedAt;

    /** Why the last try failed, if it did. */
    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    OutboxMessage(String type, Long aggregateId, Instant now) {
        this.type = type;
        this.aggregateId = aggregateId;
        this.createdAt = now;
        this.availableAt = now;
    }

    /** Claimed for one delivery: no other relay takes it before {@code until}, when it is due again unless processed. */
    void claim(Instant until) {
        attempts++;
        availableAt = until;
    }

    void processed(Instant now) {
        processedAt = now;
        lastError = null;
    }

    /** Not done yet: due again at {@code at}. A message already processed (by a repeated delivery) stays processed. */
    void retryAt(Instant at, String error) {
        if (processedAt != null) {
            return;
        }
        availableAt = at;
        lastError = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
