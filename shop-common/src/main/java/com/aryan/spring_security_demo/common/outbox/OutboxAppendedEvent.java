package com.aryan.spring_security_demo.common.outbox;

/** An outbox message was written; once its transaction commits, it can be delivered. */
public record OutboxAppendedEvent(Long messageId) {
}
