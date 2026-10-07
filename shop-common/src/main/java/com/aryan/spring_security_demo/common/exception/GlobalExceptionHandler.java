package com.aryan.spring_security_demo.common.exception;

import io.jsonwebtoken.JwtException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single global error handler for every controller. Each exception is mapped to
 * an RFC 7807 {@link ProblemDetail} ({@code application/problem+json}) whose HTTP
 * status matches the failure, so controllers can just throw and return the happy
 * path — no repetitive {@code try/catch}.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring's own MVC
 * exceptions (malformed JSON, wrong HTTP method, missing params, unknown route,
 * unsupported media type, …) keep their correct 4xx status and are rendered as
 * {@code ProblemDetail} too — instead of being swallowed by the {@link
 * #handleUnexpected(Exception) Exception} fallback and turned into 500s.
 *
 * <p>Resolution is most-specific-first: framework exceptions are handled by the
 * base class, the domain exceptions below by their own handlers, and anything
 * genuinely unexpected by the {@code Exception} fallback.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -----------------------------------------------------------------------
    // 4XX — client mistakes. Logged at DEBUG only: they are the caller's fault,
    // not the application's, so they must not pollute the ERROR log. The detail
    // message here is a developer-authored business message (e.g. "category not
    // found"), never an internal/framework message, so it is safe to return.
    // -----------------------------------------------------------------------

    /** 404 — the requested resource does not exist (every module's "not found" extends this). */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        log.debug("404 Not found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage());
    }

    /**
     * 409 — the request conflicts with current state: a duplicate, an illegal
     * order-status change, too little stock, a product still in orders, an empty
     * cart. Each exception names its own title (see {@link ConflictException}).
     */
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        log.debug("409 {}: {}", ex.getTitle(), ex.getMessage());
        return problem(HttpStatus.CONFLICT, ex.getTitle(), ex.getMessage());
    }

    /** 401 — a bad or expired JWT surfaced from within a controller. */
    @ExceptionHandler(JwtException.class)
    public ProblemDetail handleJwt(JwtException ex) {
        log.debug("401 Authentication failed: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", "Invalid or expired token");
    }

    /**
     * 401 — a credential was refused, e.g. an unknown, expired or revoked refresh
     * token. The detail is the exception's fixed public one, the same whatever the
     * reason, so a caller cannot probe for valid credentials.
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ProblemDetail handleAuthenticationFailed(AuthenticationFailedException ex) {
        log.debug("401 Authentication failed: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", ex.getDetail());
    }

    /**
     * 401 — login with a wrong email/password. The detail is deliberately generic
     * so it cannot be used to probe which accounts exist.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        log.debug("401 Bad credentials: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", "Invalid email or password");
    }

    /**
     * 403 — a service-layer ownership check (see {@code AuthUtils}) denied access
     * from within the request dispatch. Edge authorization (role rules in the
     * filter chain) is rendered by {@code ApiAccessDeniedHandler} instead — both
     * emit the same body. Handled explicitly so the {@link #handleUnexpected(Exception)
     * Exception} catch-all can never turn a legitimate 403 into a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.debug("403 Access denied: {}", ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Access denied", "You do not have permission to perform this action");
    }

    /**
     * 400 — {@code @Valid} on a {@code @RequestBody}, with a field-by-field
     * breakdown. Overrides the base class hook so we can attach the {@code errors}
     * map while still going through {@code handleExceptionInternal}.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        log.debug("400 Validation failed: {}", errors);
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * 400 — a request Bean Validation can't judge: an unknown sort field, a
     * tampered pagination cursor, a file that isn't an image. Each exception names
     * its own title (see {@link BadRequestException}).
     */
    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        log.debug("400 {}: {}", ex.getTitle(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, ex.getTitle(), ex.getMessage());
    }

    /**
     * 400 — one field failed a check made in the service (e.g. a wrong current
     * password). Shaped like a validation failure, with the message under the
     * offending field, so clients render it exactly like an {@code @Valid} error.
     */
    @ExceptionHandler(FieldValidationException.class)
    public ProblemDetail handleFieldValidation(FieldValidationException ex) {
        log.debug("400 Invalid {}: {}", ex.getField(), ex.getMessage());
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", ex.getMessage());
        problem.setProperty("errors", Map.of(ex.getField(), ex.getMessage()));
        return problem;
    }

    /**
     * 400 — constraints on {@code @Validated} controller method parameters. Not
     * covered by the base class (it is a Bean Validation exception, not an MVC
     * one), so it stays a plain {@code @ExceptionHandler}.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleParamConstraints(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        log.debug("400 Constraint violation: {}", errors);
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid");
        problem.setProperty("errors", errors);
        return problem;
    }

    // -----------------------------------------------------------------------
    // 409 — persistence-layer conflicts that Spring has translated into its
    // DataAccessException hierarchy. Logged at WARN (not ERROR): these are
    // expected concurrency/race events under load, not application bugs — but
    // they are worth surfacing, unlike the 4xx client mistakes above. The detail
    // is deliberately generic: a DataAccessException message can carry raw SQL,
    // constraint names and vendor error codes that must never reach the client.
    // -----------------------------------------------------------------------

    /**
     * 409 — a database constraint rejected the write: a unique check lost a
     * check-then-act race (two concurrent creates both passed the
     * {@code existsBy...} guard), or a delete hit a row that other rows still
     * reference. Returned as a conflict rather than being swallowed by the
     * {@link #handleUnexpected(Exception) 500 catch-all}. The title is generic
     * because it covers both cases.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("409 Data integrity violation", ex);
        return problem(HttpStatus.CONFLICT, "Data conflict",
                "The request conflicts with existing data");
    }

    /**
     * 409 — an optimistic-lock check failed because another request modified the
     * same {@code @Version}ed entity first. The client should refetch and retry.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("409 Optimistic lock conflict", ex);
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The resource was modified by another request; please retry");
    }

    /**
     * 409 — the database gave up on a row lock: two transactions deadlocked (it
     * rolls one back), or a lock wait timed out. The losing request changed
     * nothing, so the client can retry it.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail handleLockConflict(PessimisticLockingFailureException ex) {
        log.warn("409 Lock conflict", ex);
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The resource was being changed by another request; please retry");
    }

    // -----------------------------------------------------------------------
    // 5XX — the application's fault. Logged at ERROR with the full stack trace,
    // but the client only ever sees a generic message: the real exception text
    // can leak stack traces, SQL fragments, class names or file paths that help
    // an attacker and mean nothing to a legitimate caller.
    // -----------------------------------------------------------------------

    /** 500 — the catch-all for anything not handled above. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, detail != null ? detail : title);
        problem.setTitle(title);
        return problem;
    }
}
