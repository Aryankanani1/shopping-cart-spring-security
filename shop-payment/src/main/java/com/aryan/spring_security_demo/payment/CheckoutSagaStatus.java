package com.aryan.spring_security_demo.payment;

/**
 * Where an order's checkout saga stands (docs/payment-design.md, section 13), and
 * the moves it can make.
 *
 * <pre>
 *   AWAITING_PAYMENT ──▶ COMPLETED ──▶ COMPENSATING ──▶ COMPENSATED
 *        │   │                │              ▲               ▲
 *        │   └────────────────┼──────────────┘               │
 *        └────────────────────┴──────────────────────────────┘
 * </pre>
 *
 * <p>{@code AWAITING_PAYMENT} once the order is booked (step 1), until a payment
 * succeeds and the order is paid (steps 2 and 3): {@code COMPLETED}. Undoing it is
 * {@code COMPENSATING} while a compensation is still owed (the order's cancellation
 * after the last failed payment, or a refund), and {@code COMPENSATED} once every
 * step done so far is undone. A completed checkout is compensated too if its order
 * is cancelled later, before it ships: the money goes back and so does the stock.
 */
public enum CheckoutSagaStatus {

    AWAITING_PAYMENT,
    COMPLETED,
    COMPENSATING,
    COMPENSATED;

    /** Whether a saga in this state may move to {@code target}. */
    public boolean canTransitionTo(CheckoutSagaStatus target) {
        return switch (this) {
            case AWAITING_PAYMENT -> target == COMPLETED || target == COMPENSATING || target == COMPENSATED;
            case COMPLETED -> target == COMPENSATING || target == COMPENSATED;
            case COMPENSATING -> target == COMPENSATED;
            case COMPENSATED -> false;
        };
    }
}
