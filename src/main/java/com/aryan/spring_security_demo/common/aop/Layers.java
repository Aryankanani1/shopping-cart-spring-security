package com.aryan.spring_security_demo.common.aop;

import org.aspectj.lang.annotation.Pointcut;

/**
 * Named pointcuts for the application's layers, shared by the aspects.
 *
 * <p>The code is organised by feature module ({@code catalog}, {@code order}, …),
 * so a layer is no longer a package: each module holds its own controllers and
 * services. Layers are therefore matched by stereotype annotation, restricted to
 * this application's packages — without that restriction, library controllers
 * (e.g. springdoc's {@code /v3/api-docs}) would be advised too.
 *
 * <p>Reference these by fully qualified name, e.g.
 * {@code @Before("com.aryan.spring_security_demo.common.aop.Layers.requestHandlers()")}.
 */
public class Layers {

    /** Every public method of one of our {@code @RestController}s — one entry point per request. */
    @Pointcut("within(com.aryan.spring_security_demo..*)"
            + " && within(@org.springframework.web.bind.annotation.RestController *)"
            + " && execution(public * *(..))")
    public void requestHandlers() {
    }

    /**
     * Every public method of one of our {@code @Service}s. Security infrastructure
     * is left out: it runs on every request, and an unknown email at login would
     * otherwise be logged as a service failure.
     */
    @Pointcut("within(com.aryan.spring_security_demo..*)"
            + " && !within(com.aryan.spring_security_demo.identity.security..*)"
            + " && within(@org.springframework.stereotype.Service *)"
            + " && execution(public * *(..))")
    public void services() {
    }
}
