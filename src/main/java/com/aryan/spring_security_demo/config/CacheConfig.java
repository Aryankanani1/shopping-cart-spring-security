package com.aryan.spring_security_demo.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring's cache abstraction and registers an in-memory
 * {@link ConcurrentMapCacheManager}. This ships with spring-context, so it adds
 * no new dependency and is a sensible default for read-heavy reference data
 * (categories, product catalog) that changes rarely.
 * <p>
 * For a distributed/production setup, swap this bean for a Redis/Caffeine
 * cache manager — the {@code @Cacheable} annotations elsewhere stay unchanged.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache of the full category list, keyed by a constant. */
    public static final String CATEGORIES_CACHE = "categories";

    /** Cache of the converted product catalog (DTOs), keyed by a constant. */
    public static final String PRODUCTS_CACHE = "products";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(CATEGORIES_CACHE, PRODUCTS_CACHE);
    }
}
