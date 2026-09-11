package com.aryan.spring_security_demo.security.ratelimit;

import com.aryan.spring_security_demo.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Throttles the unauthenticated auth endpoints ({@code /auth/**}: login, refresh,
 * logout) so credential-stuffing and refresh-token guessing can't hammer them at
 * machine speed. Everything else is unaffected.
 *
 * <p>Wired into the security filter chain ahead of {@code AuthTokenFilter} (see
 * {@code ShopConfig}) — like {@code AuthTokenFilter} it is a plain class, not a
 * {@code @Component}, so it isn't pulled into {@code @WebMvcTest} slices without
 * its collaborators. An over-limit caller is turned away with a 429 before any
 * authentication work runs, with an RFC 7807 {@link ProblemDetail} body (matching
 * {@code JwtEntryPoint} / the global handler) plus a {@code Retry-After} header so
 * a well-behaved client knows when to retry.
 *
 * <p>Extends {@link OncePerRequestFilter} so it also runs exactly once even if the
 * container invokes the chain more than once (e.g. on async dispatch).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // Thread-safe once configured; reused across requests instead of per-call.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final String authPathPrefix;

    public RateLimitFilter(RateLimitService rateLimitService,
                           RateLimitProperties properties,
                           String apiPrefix) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.authPathPrefix = apiPrefix + "/auth/";
    }

    /** Only the auth endpoints are throttled, and only while enabled. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || !pathWithinApplication(request).startsWith(authPathPrefix);
    }

    /**
     * The request path relative to the context, derived from the request URI rather
     * than {@code getServletPath()} — the latter is left empty by MockMvc's mock
     * request, which would silently disable matching under test.
     */
    private static String pathWithinApplication(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Keyed on the socket peer address. Note: behind a reverse proxy this is the
        // proxy's IP unless a trusted-proxy ForwardedHeaderFilter is configured;
        // X-Forwarded-For is client-spoofable and is intentionally not trusted here.
        String clientKey = request.getRemoteAddr();
        RateLimitService.Decision decision = rateLimitService.tryConsume(clientKey);

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = pathWithinApplication(request);
        long retryAfterSeconds = Math.max(1, (long) Math.ceil(decision.retryAfterMillis() / 1000.0));
        log.debug("429 Rate limit exceeded for {} on {}", clientKey, path);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Please retry in " + retryAfterSeconds + " second(s).");
        problem.setTitle("Too many requests");
        problem.setProperty("path", path);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        MAPPER.writeValue(response.getOutputStream(), problem);
    }
}
