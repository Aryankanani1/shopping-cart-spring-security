package com.aryan.spring_security_demo.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Entry point for requests that are authenticated but lack the required
 * authority — e.g. a non-admin hitting an admin-only catalog write. Since the
 * authorization rules now live in the {@code HttpSecurity} filter chain (not in
 * controller {@code @PreAuthorize} annotations), the {@link AccessDeniedException}
 * is raised by the authorization filter and never reaches
 * {@code GlobalExceptionHandler} — this class is where that 403 is rendered.
 *
 * <p>It emits the same RFC 7807 {@link ProblemDetail}
 * ({@code application/problem+json}) shape — same title and detail — that
 * {@code GlobalExceptionHandler} uses for the service-layer ownership 403s, so a
 * caller sees an identical body regardless of whether the check fired at the edge
 * or in the domain. It is the 403 sibling of {@code JwtEntryPoint}'s 401.
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    // Thread-safe once configured; reused across requests instead of per-call.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
        problem.setTitle("Access denied");
        problem.setProperty("path", request.getServletPath());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        MAPPER.writeValue(response.getOutputStream(), problem);
    }
}
