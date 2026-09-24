package com.aryan.spring_security_demo.exception;

/**
 * Thrown when an uploaded file is not an acceptable product image — empty, or of
 * a type the storefront can't render. Rendered as {@code 400 Bad Request}.
 */
public class InvalidImageException extends RuntimeException {
    public InvalidImageException(String message) {
        super(message);
    }
}
