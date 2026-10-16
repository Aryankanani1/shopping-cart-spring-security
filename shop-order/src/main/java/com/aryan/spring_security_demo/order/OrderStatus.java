package com.aryan.spring_security_demo.order;

/**
 * The lifecycle states an {@link com.aryan.spring_security_demo.order.Order} moves
 * through, plus the legal transitions between them. Keeping the state machine on
 * the enum itself — rather than scattering {@code if (status == …)} checks across
 * the service — means there is exactly one place that answers "can this order go
 * from A to B?", and an illegal jump is rejected the same way everywhere.
 *
 * <pre>
 *   PENDING ──▶ PAID ──▶ PROCESSING ──▶ SHIPPED ──▶ DELIVERED   (terminal)
 *      │
 *      ▼
 *   CANCELLED   (terminal)
 * </pre>
 *
 * <p>An order waits in {@code PENDING}, its stock reserved, until it is paid; only
 * a successful payment moves it to {@code PAID} (see {@code OrderService.markPaid}),
 * and only a paid order is fulfilled. Fulfillment moves forward only;
 * {@code DELIVERED} and {@code CANCELLED} are terminal. An order can be cancelled
 * only while unpaid: giving the money back for a paid one needs refunds, which
 * the payment module doesn't do yet.
 */
public enum OrderStatus {

    PENDING,
    PAID,
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
            case PENDING    -> target == PAID || target == CANCELLED;
            case PAID       -> target == PROCESSING;
            case PROCESSING -> target == SHIPPED;
            case SHIPPED    -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

}
