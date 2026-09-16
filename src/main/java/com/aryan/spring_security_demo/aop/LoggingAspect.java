package com.aryan.spring_security_demo.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * Cross-cutting logging for the service layer. A single {@code @Around} advice
 * wraps every public method on any {@code @Service} bean under
 * {@code com.aryan.spring_security_demo.service}, so entry/exit/timing logging
 * lives in one place instead of being scattered through each service.
 * <p>
 * This is proxy-based Spring AOP (the {@code spring-boot-starter-aop} pulls in
 * AspectJ annotations, but weaving is done via runtime proxies) — advice only
 * fires when a service is called <em>through</em> its proxy from another bean,
 * not on self-invocation within the same class.
 * <p>
 * Timing is logged at DEBUG to keep normal INFO logs quiet; exceptions thrown
 * out of a service are logged at ERROR with the elapsed time before rethrowing
 * so the original behaviour (and the {@code GlobalExceptionHandler}) is
 * unchanged.
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    /**
     * Any method on a Spring bean declared in the service package tree. Using the
     * package pointcut (rather than {@code @annotation}) keeps every current and
     * future service covered without touching the service code.
     */
    @Pointcut("execution(public * com.aryan.spring_security_demo.service..*.*(..))")
    public void serviceMethods() {
    }

    @Around("serviceMethods()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String target = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String method = joinPoint.getSignature().getName();

        if (log.isDebugEnabled()) {
            log.debug("→ {}.{}({})", target, method, joinPoint.getArgs().length);
        }

        long startNanos = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.debug("← {}.{} completed in {} ms", target, method, elapsedMs);
            return result;
        } catch (Throwable ex) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.error("✗ {}.{} failed after {} ms: {}: {}",
                    target, method, elapsedMs,
                    ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }
}
