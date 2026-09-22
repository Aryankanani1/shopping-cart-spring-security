package com.aryan.spring_security_demo.enums;

import org.junit.jupiter.api.Test;

import static com.aryan.spring_security_demo.enums.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The order lifecycle state machine, asserted edge by edge. This is the single
 * source of truth for "can an order go from A to B?", so every legal and illegal
 * hop is pinned here — a future edit that widens or narrows the graph has to
 * change a test, not slip through silently.
 */
class OrderStatusTest {

    @Test
    void forwardFulfilmentTransitionsAreAllowed() {
        assertThat(PENDING.canTransitionTo(PROCESSING)).isTrue();
        assertThat(PROCESSING.canTransitionTo(SHIPPED)).isTrue();
        assertThat(SHIPPED.canTransitionTo(DELIVERED)).isTrue();
    }

    @Test
    void cancellationIsAllowedOnlyBeforeShipping() {
        assertThat(PENDING.canTransitionTo(CANCELLED)).isTrue();
        assertThat(PROCESSING.canTransitionTo(CANCELLED)).isTrue();
        // Once shipped, the goods are in transit — no cancel.
        assertThat(SHIPPED.canTransitionTo(CANCELLED)).isFalse();
    }

    @Test
    void backwardAndSkippingTransitionsAreRejected() {
        assertThat(PROCESSING.canTransitionTo(PENDING)).isFalse();
        assertThat(SHIPPED.canTransitionTo(PROCESSING)).isFalse();
        assertThat(PENDING.canTransitionTo(SHIPPED)).isFalse();
        assertThat(PENDING.canTransitionTo(DELIVERED)).isFalse();
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        for (OrderStatus target : values()) {
            assertThat(DELIVERED.canTransitionTo(target))
                    .as("DELIVERED -> %s", target).isFalse();
            assertThat(CANCELLED.canTransitionTo(target))
                    .as("CANCELLED -> %s", target).isFalse();
        }
        assertThat(DELIVERED.isTerminal()).isTrue();
        assertThat(CANCELLED.isTerminal()).isTrue();
        assertThat(PENDING.isTerminal()).isFalse();
        assertThat(PROCESSING.isTerminal()).isFalse();
        assertThat(SHIPPED.isTerminal()).isFalse();
    }
}
