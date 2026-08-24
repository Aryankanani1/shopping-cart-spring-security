package com.aryan.spring_security_demo.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * First thing to run at boot: echo the effective configuration so an operator can
 * confirm — from the logs alone — which DB, profile and security settings the
 * instance actually came up with. Secrets are never printed, only whether they
 * are present.
 *
 * <p>Uses {@link CommandLineRunner} because it only needs the raw argument array,
 * not the parsed {@code ApplicationArguments}.
 */
@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class StartupInfoRunner implements CommandLineRunner {

    private final Environment env;

    @Override
    public void run(String... args) {
        log.info("======================================================================");
        log.info("  Shopping Cart API — bootstrap");
        log.info("----------------------------------------------------------------------");
        log.info("  Application    : {}", env.getProperty("spring.application.name", "unknown"));
        log.info("  Active profiles: {}", activeProfiles());
        log.info("  API prefix     : {}", env.getProperty("api.prefix", "(default)"));
        log.info("  Datasource     : {}", maskUrl(env.getProperty("spring.datasource.url")));
        log.info("  DDL auto       : {}", env.getProperty("spring.jpa.hibernate.ddl-auto", "(default)"));
        log.info("  Open-in-view   : {}", env.getProperty("spring.jpa.open-in-view", "(default)"));
        log.info("  Launch args    : {}", args.length == 0 ? "(none)" : String.join(" ", args));
        log.info("  JWT secret     : {}", isPresent("auth.token.jwtSecret") ? "configured" : "MISSING");
        log.info("  JWT expiry(ms) : {}", env.getProperty("auth.token.expirationInMils", "(default)"));
        log.info("======================================================================");
    }

    private String activeProfiles() {
        String[] profiles = env.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(", ", profiles);
    }

    private boolean isPresent(String key) {
        String value = env.getProperty(key);
        return value != null && !value.isBlank();
    }

    /** Hide credentials that may be embedded in a JDBC URL before logging it. */
    private String maskUrl(String url) {
        if (url == null || url.isBlank()) {
            return "(not set)";
        }
        return url.replaceAll("(password=)[^&]+", "$1****")
                  .replaceAll("(//)[^/@]+@", "$1****@");
    }
}
