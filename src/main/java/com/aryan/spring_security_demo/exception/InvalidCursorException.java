package com.aryan.spring_security_demo.exception;

/**
 * Thrown when a client supplies a pagination cursor that is not a well-formed,
 * previously-issued token. Mapped to 400 by the global handler — the cursor is
 * opaque, so a bad one is a caller mistake, never a server error.
 */
public class InvalidCursorException extends RuntimeException {
    public InvalidCursorException(String token) {
        super("Invalid pagination cursor: '" + token + "'");
    }
}
