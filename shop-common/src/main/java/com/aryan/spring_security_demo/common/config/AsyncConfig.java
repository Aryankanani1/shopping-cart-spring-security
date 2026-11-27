package com.aryan.spring_security_demo.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Turns on Spring's {@code @Async} support, which runs
 * {@code OutboxAfterCommitDispatcher} on Spring Boot's task executor. Kept as a
 * dedicated config, like {@link SchedulingConfig}, so the app's use of background
 * threads is discoverable in one place.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {
}
