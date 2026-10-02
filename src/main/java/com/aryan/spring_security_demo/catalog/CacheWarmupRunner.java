package com.aryan.spring_security_demo.catalog;

import com.aryan.spring_security_demo.common.config.StartupProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Last in the pipeline: pre-loads the category cache so the very first
 * storefront request doesn't pay the cold-cache database round-trip. Runs after
 * {@code DefaultDataRunner} so freshly-seeded categories are included.
 *
 * <p>The population is a side effect of calling the {@code @Cacheable}
 * {@link CategoryServiceInterface#getAllCategoryDtos()}; this runner just
 * triggers it once and reports how much was warmed.
 */
@Component
@Order(40)
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupRunner implements ApplicationRunner {

    private final CategoryServiceInterface categoryService;
    private final StartupProperties startupProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!startupProperties.getCache().isWarmupEnabled()) {
            log.info("[cache] Warm-up disabled (app.startup.cache.warmup-enabled=false)");
            return;
        }

        long start = System.currentTimeMillis();
        int categories = categoryService.getAllCategoryDtos().size();
        long elapsed = System.currentTimeMillis() - start;

        log.info("[cache] Warm-up complete — {} categories cached in {} ms", categories, elapsed);
    }
}
