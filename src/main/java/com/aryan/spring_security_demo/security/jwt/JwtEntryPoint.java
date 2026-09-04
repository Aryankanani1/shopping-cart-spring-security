package com.aryan.spring_security_demo.security.jwt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Entry point for requests that reach a secured endpoint without valid
 * authentication. These are rejected by Spring Security's filter chain before
 * they ever reach a controller, so {@code GlobalExceptionHandler} never sees
 * them — this class is where the 401 is rendered.
 *
 * <p>It emits the same RFC 7807 {@link ProblemDetail}
 * ({@code application/problem+json}) shape the global handler uses for every
 * other error, so a 401 body is consistent with the 403/404/400 responses. The
 * detail is deliberately generic — it never echoes the underlying
 * {@link AuthenticationException} message, which can leak internals.
 */
@Component
public class JwtEntryPoint implements AuthenticationEntryPoint {

    // Thread-safe once configured; reused across requests instead of per-call.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource");
        problem.setTitle("Unauthorized");
        problem.setProperty("path", request.getServletPath());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        MAPPER.writeValue(response.getOutputStream(), problem);
    }
}
