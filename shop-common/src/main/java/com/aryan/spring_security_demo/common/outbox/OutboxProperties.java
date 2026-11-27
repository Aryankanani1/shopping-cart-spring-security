package com.aryan.spring_security_demo.common.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Outbox delivery settings ({@code app.outbox.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

    /** Most messages one run of {@link OutboxRelayJob} claims. */
    private int batchSize = 20;

    /**
     * How long a claimed message is left to the relay that claimed it. Past this, a
     * message never marked processed (the app stopped mid-delivery) is delivered
     * again; it must comfortably outlast a whole batch's handlers.
     */
    private Duration claimTimeout = Duration.ofMinutes(5);

    /** The wait before a failed message's first retry, doubled after each further try, up to {@link #maxBackoff}. */
    private Duration backoff = Duration.ofSeconds(10);

    private Duration maxBackoff = Duration.ofMinutes(10);

    /** How long processed messages are kept, to see what happened, before they are deleted. */
    private Duration retention = Duration.ofDays(7);
}
