package com.aryan.spring_security_demo.common.exception;

import java.time.Duration;

/**
 * Something the request needs is known to be failing (an outside service whose
 * circuit breaker is open), so the request is refused instead of tried.
 * {@code GlobalExceptionHandler} answers 503 with the message as the detail and a
 * {@code Retry-After} of {@link #getRetryAfter()}, so the client knows when to come
 * back.
 */
public class ServiceUnavailableException extends RuntimeException {

    private final Duration retryAfter;

    public ServiceUnavailableException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
