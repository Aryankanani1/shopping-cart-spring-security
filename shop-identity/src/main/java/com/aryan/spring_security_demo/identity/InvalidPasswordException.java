package com.aryan.spring_security_demo.identity;

import com.aryan.spring_security_demo.common.exception.FieldValidationException;

/**
 * A password change was rejected: the current password didn't match, or the new
 * one equals it. Mapped to 400 by the global handler as a field error (keyed by
 * {@link #getField()}) so the client can show it next to the right input.
 *
 * <p>Deliberately not 401 — the caller <em>is</em> authenticated, and a 401 would
 * make clients attempt a token refresh instead of showing the message.
 */
public class InvalidPasswordException extends FieldValidationException {


    public InvalidPasswordException(String field, String message) {
        super(field, message);
    }

}
