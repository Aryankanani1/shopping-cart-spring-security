package com.aryan.spring_security_demo.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A webhook event the shop has received (V14 migration). The unique event id is
 * what makes processing exactly-once: it is recorded in the same transaction as
 * the change the event makes, so a repeat of the event finds it and does nothing.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payment_webhook_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_webhook_event_event_id", columnNames = "event_id"))
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The provider's id for the event. */
    @Column(name = "event_id", nullable = false, updatable = false, length = 64)
    private String eventId;

    @Column(nullable = false, updatable = false, length = 64)
    private String type;

    /** The payment it was about, when the shop knows it. Not a foreign key: an event can name an unknown one. */
    @Column(name = "payment_id", updatable = false)
    private Long paymentId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public PaymentWebhookEvent(String eventId, String type, Long paymentId, Instant receivedAt) {
        this.eventId = eventId;
        this.type = type;
        this.paymentId = paymentId;
        this.receivedAt = receivedAt;
    }
}
