package com.aryan.spring_security_demo.common.exception;

/**
 * A credential was refused. {@code GlobalExceptionHandler} answers 401 with
 * {@link #getDetail()}, which is the same for every reason a credential of that
 * kind fails, so a caller can't probe which check it tripped. The message, with
 * the actual reason, is only logged.
 */
public class AuthenticationFailedException extends RuntimeException {

    private final String detail;

    public AuthenticationFailedException(String detail, String message) {
        super(message);
        this.detail = detail;
    }

    public String getDetail() {
        return detail;
    }
}
