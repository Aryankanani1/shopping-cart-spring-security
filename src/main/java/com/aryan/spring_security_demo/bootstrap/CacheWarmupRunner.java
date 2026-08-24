package com.aryan.spring_security_demo.bootstrap;

import com.aryan.spring_security_demo.Service.cache.CatalogCacheService;
import com.aryan.spring_security_demo.config.StartupProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Last in the pipeline: pre-loads the read-heavy catalog caches so the very first
 * user request doesn't pay the cold-cache database round-trip. Runs after
 * {@code DefaultDataRunner} so freshly-seeded categories are included.
 *
 * <p>The actual population is a side effect of calling the {@code @Cacheable}
 * methods on {@link CatalogCacheService}; this runner just triggers them once and
 * reports how much was warmed.
 */
@Component
@Order(40)
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupRunner implements ApplicationRunner {

    private final CatalogCacheService catalogCacheService;
    private final StartupProperties startupProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!startupProperties.getCache().isWarmupEnabled()) {
            log.info("[cache] Warm-up disabled (app.startup.cache.warmup-enabled=false)");
            return;
        }

        long start = System.currentTimeMillis();
        int categories = catalogCacheService.getAllCategories().size();
        int products = catalogCacheService.getAllProducts().size();
        long elapsed = System.currentTimeMillis() - start;

        log.info("[cache] Warm-up complete — {} categories, {} products cached in {} ms",
                categories, products, elapsed);
    }
}
