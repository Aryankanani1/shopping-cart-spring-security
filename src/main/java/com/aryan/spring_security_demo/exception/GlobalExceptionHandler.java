package com.aryan.spring_security_demo.exception;

import io.jsonwebtoken.JwtException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

    /** 404 — the requested resource does not exist. */
    @ExceptionHandler({
            ResourceNotFoundException.class,
            CartNotFoundException.class,
            ProductNotFoundException.class,
            CategoryNotFoundException.class,
            ImageNotFoundException.class,
            UserNoFoundException.class
    })
    public ProblemDetail handleNotFound(RuntimeException ex) {
        log.debug("404 Not found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage());
    }

    /** 409 — the resource being created already exists. */
    @ExceptionHandler(AlreadyExistsException.class)
    public ProblemDetail handleConflict(AlreadyExistsException ex) {
        log.debug("409 Conflict: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Resource already exists", ex.getMessage());
    }

    /** 401 — a bad or expired JWT surfaced from within a controller. */
    @ExceptionHandler(JwtException.class)
    public ProblemDetail handleJwt(JwtException ex) {
        log.debug("401 Authentication failed: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", "Invalid or expired token");
    }

    /**
     * 403 — method security (@PreAuthorize) denied access. Handled explicitly so
     * the {@link #handleUnexpected(Exception) Exception} catch-all can never turn
     * a legitimate 403 into a 500.
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
