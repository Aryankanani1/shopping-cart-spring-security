package com.aryan.spring_security_demo.exception;

/**
 * The presented refresh token is unknown, expired, or revoked. Mapped to 401 by
 * the global handler with a deliberately generic detail, so a caller cannot
 * distinguish "never existed" from "revoked" and probe for valid tokens.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
