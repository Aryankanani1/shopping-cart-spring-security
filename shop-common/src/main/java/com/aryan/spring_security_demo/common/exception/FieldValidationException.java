package com.aryan.spring_security_demo.common.exception;

/**
 * One request field failed a check made in the service (a wrong current
 * password, say). {@code GlobalExceptionHandler} answers 400 shaped like an
 * {@code @Valid} failure, with the message under {@link #getField()} in
 * {@code errors}, so clients show it next to the right input.
 */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
