package com.aryan.spring_security_demo.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns Bean Validation failures into an RFC 7807 {@link ProblemDetail}
 * ({@code application/problem+json}) so clients and tooling get a standard,
 * machine-readable error shape. The per-field messages are attached as an
 * {@code errors} extension member. Covers custom constraints such as
 * {@code @NoProfanity}.
 */
@RestControllerAdvice
public class ValidationExceptionHandler {

    /** Fired for {@code @Valid} on a {@code @RequestBody}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return problem(errors);
    }

    /**
     * Fired for constraints placed directly on controller method parameters
     * (e.g. {@code @RequestParam @NotBlank}) once the class is {@code @Validated}.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleParamConstraints(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return problem(errors);
    }

    private ProblemDetail problem(Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more fields are invalid");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }
}
