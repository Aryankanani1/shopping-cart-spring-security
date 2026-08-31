package com.aryan.spring_security_demo.response;

/**
 * Standard success envelope: a human-readable {@code message} plus the typed
 * {@code data} payload (a DTO, a collection of DTOs, or {@code null}).
 *
 * <p>Immutable and generic so the payload type is documented in the OpenAPI spec
 * and checked at compile time. Errors are never wrapped in this type — the global
 * handler returns them as RFC 7807 {@link org.springframework.http.ProblemDetail}.
 */
public record ApiResponse<T>(String message, T data) {
}
