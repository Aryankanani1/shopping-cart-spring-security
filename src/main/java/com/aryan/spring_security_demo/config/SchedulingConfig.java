package com.aryan.spring_security_demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's {@code @Scheduled} support. Kept as a dedicated config (rather
 * than an annotation on the main class) so the app's use of background scheduling
 * is discoverable in one place. Currently drives the expired refresh-token purge
 * in {@code RefreshTokenCleanupService}.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
