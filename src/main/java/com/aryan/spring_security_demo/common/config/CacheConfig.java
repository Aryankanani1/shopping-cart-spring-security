package com.aryan.spring_security_demo.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring's cache abstraction and registers an in-memory
 * {@link ConcurrentMapCacheManager}. This ships with spring-context, so it adds
 * no new dependency and is a sensible default for read-heavy reference data
 * (the category list) that changes rarely.
 * <p>
 * Wrapped in a {@link TransactionAwareCacheManagerProxy} so cache writes and
 * evictions made inside a transaction apply only once it commits: otherwise a
 * read racing a category change could re-cache the old list between the
 * eviction and the commit, or a rolled-back change could still evict.
 * <p>
 * The cache is per instance. For a multi-instance setup, swap this bean for a
 * shared (Redis) cache manager; the {@code @Cacheable} annotations stay unchanged.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    /** Cache of the full category list (as DTOs), keyed by a constant. */
    public static final String CATEGORIES_CACHE = "categories";

    @Bean
    public CacheManager cacheManager() {
        return new TransactionAwareCacheManagerProxy(new ConcurrentMapCacheManager(CATEGORIES_CACHE));
    }
}
