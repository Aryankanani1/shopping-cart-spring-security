package com.aryan.spring_security_demo.order;

import com.aryan.spring_security_demo.common.exception.BadRequestException;

/**
 * Thrown when a client supplies a pagination cursor that is not a well-formed,
 * previously-issued token. Mapped to 400 by the global handler — the cursor is
 * opaque, so a bad one is a caller mistake, never a server error.
 */
public class InvalidCursorException extends BadRequestException {
    public InvalidCursorException(String token) {
        super("Invalid pagination cursor", "Invalid pagination cursor: '" + token + "'");
    }
}
