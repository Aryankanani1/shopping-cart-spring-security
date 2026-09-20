package com.aryan.spring_security_demo.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Turns on Spring's {@code @Scheduled} support. Kept as a dedicated config (rather
 * than an annotation on the main class) so the app's use of background scheduling
 * is discoverable in one place. Drives the expired refresh-token purge in
 * {@code RefreshTokenCleanupService} and the rate-limit bucket sweep in
 * {@code RateLimitService}.
 *
 * <p>Also enables <strong>ShedLock</strong> ({@code @EnableSchedulerLock}) so that
 * a {@code @SchedulerLock}-annotated task runs on only one instance at a time in a
 * multi-node deployment. {@code defaultLockAtMostFor} is the safety net that
 * releases a lock if the holder crashes mid-task without unlocking; individual
 * tasks may override it. The lock is stored in a {@code shedlock} table via the
 * {@link JdbcTemplateLockProvider}; {@code usingDbTime()} takes the timestamp from
 * the database so lock timing doesn't depend on each node's (possibly skewed) clock.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
