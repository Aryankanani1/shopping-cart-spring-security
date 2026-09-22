package com.aryan.spring_security_demo.enums;

/**
 * The lifecycle states an {@link com.aryan.spring_security_demo.model.Order} moves
 * through, plus the legal transitions between them. Keeping the state machine on
 * the enum itself — rather than scattering {@code if (status == …)} checks across
 * the service — means there is exactly one place that answers "can this order go
 * from A to B?", and an illegal jump is rejected the same way everywhere.
 *
 * <pre>
 *   PENDING ──▶ PROCESSING ──▶ SHIPPED ──▶ DELIVERED   (terminal)
 *      │             │
 *      └──────┬──────┘
 *             ▼
 *         CANCELLED   (terminal)
 * </pre>
 *
 * <p>Fulfillment moves forward only; {@code DELIVERED} and {@code CANCELLED} are
 * terminal. An order can be cancelled only before it ships (from {@code PENDING}
 * or {@code PROCESSING}) — once it is {@code SHIPPED} the goods are in transit and
 * a cancel would need a separate returns flow, so it is disallowed here.
 */
public enum OrderStatus {

    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /** A terminal state has no outgoing transitions — the order is done. */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }

    /** Whether an order in this state may legally move to {@code target}. */
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING    -> target == PROCESSING || target == CANCELLED;
            case PROCESSING -> target == SHIPPED || target == CANCELLED;
            case SHIPPED    -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

}
