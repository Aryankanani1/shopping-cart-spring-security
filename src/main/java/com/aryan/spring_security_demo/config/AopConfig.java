package com.aryan.spring_security_demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Enables Spring's AspectJ-style auto-proxying so {@code @Aspect} beans (currently
 * {@link com.aryan.spring_security_demo.aop.LoggingAspect}) are applied. Kept as a
 * dedicated config — mirroring {@link CacheConfig}/{@link SchedulingConfig} — so
 * the app's use of AOP is discoverable in one place.
 * <p>
 * Spring Boot 4 no longer ships {@code spring-boot-starter-aop}, so auto-proxying
 * is switched on here explicitly rather than relying on that starter's
 * autoconfiguration; the AspectJ annotations come from {@code aspectjweaver}.
 */
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
}
