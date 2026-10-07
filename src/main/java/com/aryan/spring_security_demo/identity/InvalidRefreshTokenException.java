package com.aryan.spring_security_demo.identity;

import com.aryan.spring_security_demo.common.exception.AuthenticationFailedException;

/**
 * The presented refresh token is unknown, expired, or revoked. Mapped to 401 by
 * the global handler with a deliberately generic detail, so a caller cannot
 * distinguish "never existed" from "revoked" and probe for valid tokens.
 */
public class InvalidRefreshTokenException extends AuthenticationFailedException {
    public InvalidRefreshTokenException(String message) {
        super("Invalid or expired refresh token", message);
    }
}
