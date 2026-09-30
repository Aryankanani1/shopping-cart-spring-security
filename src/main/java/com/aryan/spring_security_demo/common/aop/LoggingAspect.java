package com.aryan.spring_security_demo.common.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

/**
 * Cross-cutting logging for the controller and service layers. A single
 * {@code @Around} advice wraps every public method of every {@code @Service} (see
 * {@link Layers#services()}), so entry/exit/timing logging lives in one place
 * instead of being scattered through each service.
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



    @Before("com.aryan.spring_security_demo.common.aop.Layers.requestHandlers()")
    public void logBeforeControllerMethods(JoinPoint joinPoint){
        if (log.isDebugEnabled()) {
            log.debug("⇢ [controller] {}.{}({} args)",
                    joinPoint.getSignature().getDeclaringType().getSimpleName(),
                    joinPoint.getSignature().getName(),
                    joinPoint.getArgs().length);
        }
    }

    @AfterReturning(pointcut = "com.aryan.spring_security_demo.common.aop.Layers.requestHandlers()",
            returning = "result")
    public void logAfterControllerMethods(JoinPoint joinPoint, Object result){
        if (log.isDebugEnabled()) {
            log.debug("⇠ [controller] {}.{} returned",
                    joinPoint.getSignature().getDeclaringType().getSimpleName(),
                    joinPoint.getSignature().getName());
        }
    }



    @AfterThrowing(pointcut = "com.aryan.spring_security_demo.common.aop.Layers.requestHandlers()", throwing = "exception")
    public void logAfterThrowingControllerMethod(JoinPoint joinPoint, Exception exception){
        log.error("⇡ [controller boundary] {}.{} propagated {}: {}",
                joinPoint.getSignature().getDeclaringType().getSimpleName(),
                joinPoint.getSignature().getName(),
                exception.getClass().getSimpleName(),
                exception.getMessage());
    }


    @Around("com.aryan.spring_security_demo.common.aop.Layers.services()")
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
