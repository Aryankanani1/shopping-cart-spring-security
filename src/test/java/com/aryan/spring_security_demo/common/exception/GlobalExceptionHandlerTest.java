package com.aryan.spring_security_demo.common.exception;

import com.aryan.spring_security_demo.catalog.Product;
import com.aryan.spring_security_demo.identity.InvalidPasswordException;
import com.aryan.spring_security_demo.identity.InvalidRefreshTokenException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Error responses never echo internals (SQL, constraint names, parser messages,
 * stack traces), and the auth failures never say which part was wrong.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedError_is500WithAGenericMessage() {
        ProblemDetail problem = handler.handleUnexpected(
                new IllegalStateException("SELECT * FROM users WHERE password = 'x'"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
    }

    @Test
    void constraintViolation_is409WithoutTheSql() {
        ProblemDetail problem = handler.handleDataIntegrity(new DataIntegrityViolationException(
                "Duplicate entry 'ada@example.com' for key 'users.UKq4gvg4dl2a3fpetfwspodde8e'"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Data conflict");
        assertThat(problem.getDetail()).doesNotContain("Duplicate", "UK", "ada@example.com");
    }

    @Test
    void optimisticLockConflict_is409AskingToRetry() {
        ProblemDetail problem = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException(Product.class, 5L));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).contains("retry").doesNotContain("Product");
    }

    @Test
    void badJwt_is401WithoutTheParserMessage() {
        ProblemDetail problem = handler.handleJwt(new JwtException("JWT signature does not match locally computed"));

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getDetail()).isEqualTo("Invalid or expired token");
    }

    @Test
    void refreshTokenFailures_allLookTheSame() {
        String revoked = handler.handleInvalidRefreshToken(
                new InvalidRefreshTokenException("Refresh token has been revoked")).getDetail();
        String unknown = handler.handleInvalidRefreshToken(
                new InvalidRefreshTokenException("Unknown refresh token")).getDetail();

        assertThat(revoked).isEqualTo(unknown).isEqualTo("Invalid or expired refresh token");
    }

    @Test
    void badCredentials_doesNotSayWhichPartWasWrong() {
        ProblemDetail problem = handler.handleBadCredentials(new BadCredentialsException("User not found"));

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getDetail()).isEqualTo("Invalid email or password");
    }

    @Test
    void accessDenied_is403WithAGenericMessage() {
        ProblemDetail problem = handler.handleAccessDenied(new AccessDeniedException("cart 99 belongs to user 42"));

        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getDetail()).doesNotContain("99", "42");
    }

    @Test
    void invalidPassword_isAFieldError() {
        ProblemDetail problem = handler.handleInvalidPassword(
                new InvalidPasswordException("currentPassword", "Current password is incorrect"));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties())
                .containsEntry("errors", Map.of("currentPassword", "Current password is incorrect"));
    }
}
