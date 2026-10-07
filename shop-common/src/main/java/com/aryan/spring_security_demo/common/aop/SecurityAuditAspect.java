package com.aryan.spring_security_demo.common.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Security audit trail for the request-handling layer. Every controller method is
 * a request entry point, so advising {@link Layers#requestHandlers()} gives one
 * {@code @Before}/{@code @After} pair around each and every request without
 * touching controller code.
 * <p>
 * The {@code @Before} advice records <em>who</em> is making the request (the
 * authenticated principal and its granted authorities) as the request is about to
 * be handled — i.e. after Spring Security's filter chain has already
 * authenticated/authorized it, but before the business handler runs. The
 * {@code @After} advice records that the handler for that same caller has
 * finished. Together they bracket every request with a consistent audit line.
 * <p>
 * The principal is read straight from the {@link SecurityContextHolder} — the id
 * and roles are already carried in the JWT-derived principal, so there is no
 * database round-trip — mirroring
 * {@link com.aryan.spring_security_demo.identity.security.AuthUtils}. Requests on public
 * endpoints (login, catalog reads) have no authentication yet and are logged as
 * {@code anonymous}.
 * <p>
 * This is proxy-based Spring AOP: advice only fires when a controller is invoked
 * through its Spring proxy, which is exactly how the dispatcher calls it. Logging
 * is at INFO so the audit trail is visible with the default log level.
 */
@Aspect
@Component
@Slf4j
public class SecurityAuditAspect {

    private static final String ANONYMOUS = "anonymous";

    /** Before the request is handled: record the caller and its authorities. */
    @Before("com.aryan.spring_security_demo.common.aop.Layers.requestHandlers()")
    public void auditBefore(JoinPoint joinPoint) {
        log.info("[security] → {}.{} by {}",
                joinPoint.getSignature().getDeclaringType().getSimpleName(),
                joinPoint.getSignature().getName(),
                describeCaller());
    }

    /** After the request is handled (whether it returned or threw). */
    @After("com.aryan.spring_security_demo.common.aop.Layers.requestHandlers()")
    public void auditAfter(JoinPoint joinPoint) {
        log.info("[security] ← {}.{} for {}",
                joinPoint.getSignature().getDeclaringType().getSimpleName(),
                joinPoint.getSignature().getName(),
                describeCaller());
    }

    /**
     * A short "name [authorities]" description of the current caller, or
     * {@code anonymous} when the request is unauthenticated (public endpoints).
     */
    private String describeCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ANONYMOUS;
        }
        String authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(",", "[", "]"));
        return auth.getName() + " " + authorities;
    }
}
