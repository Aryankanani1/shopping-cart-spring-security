package com.aryan.spring_security_demo.common.outbox;

/**
 * Does the work of one type of {@link OutboxMessage}. A module declares one as a
 * bean for each type it appends; {@link OutboxRelay} finds it by {@link #type()}.
 *
 * <p>Called with no transaction open, so it may call another system; it opens its
 * own short transactions for its database work (CLAUDE.md). Delivery is at least
 * once, so handling the same message twice must do the work once: check the
 * record first, and send the same idempotency key every time.
 */
public interface OutboxHandler {

    /** The message type this handler takes, unique in the application. */
    String type();

    /**
     * Do the work for the record {@code aggregateId} names.
     *
     * @return true once the work is done (or was already); false to be called again
     *         later, as is an exception
     */
    boolean handle(Long aggregateId);
}
