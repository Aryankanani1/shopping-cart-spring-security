package com.aryan.spring_security_demo.common.exception;

/**
 * The request itself is invalid in a way Bean Validation can't see: an unknown
 * sort field, a tampered cursor, a file that isn't an image.
 * {@code GlobalExceptionHandler} answers 400 with {@link #getTitle()} as the
 * problem title and the message as the detail.
 */
public class BadRequestException extends RuntimeException {

    private final String title;

    public BadRequestException(String title, String message) {
        super(message);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
