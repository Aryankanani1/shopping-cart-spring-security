package com.aryan.spring_security_demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes the system {@link Clock} as a bean so time is an injected dependency
 * rather than a hard-wired {@code Instant.now()} call. Production runs on the
 * real UTC clock; tests inject a {@link Clock#fixed} so time-dependent behaviour
 * (token expiry, cleanup cutoffs) is deterministic and testable without sleeping.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
