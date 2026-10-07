package com.aryan.spring_security_demo.common.exception;

/**
 * The request conflicts with the current state: a duplicate, an illegal state
 * change, too little stock. {@code GlobalExceptionHandler} answers 409 with
 * {@link #getTitle()} as the problem title and the message as the detail, so a
 * module can add its own conflict without touching the handler.
 */
public class ConflictException extends RuntimeException {

    private final String title;

    public ConflictException(String title, String message) {
        super(message);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
