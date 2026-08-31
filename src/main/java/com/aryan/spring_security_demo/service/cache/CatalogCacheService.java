package com.aryan.spring_security_demo.service.cache;

import com.aryan.spring_security_demo.service.product.ProductServiceInterface;
import com.aryan.spring_security_demo.config.CacheConfig;
import com.aryan.spring_security_demo.dto.ProductDto;
import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.repository.CategoryRepository;
import com.aryan.spring_security_demo.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-through cache for the catalog reference data that the storefront reads on
 * almost every request but that mutates rarely. Fetch <em>and</em> DTO conversion
 * happen inside a single read-only transaction so lazy associations
 * (category name, product images) resolve while the persistence context is still
 * open — important because {@code spring.jpa.open-in-view=false}.
 * <p>
 * The {@code @Cacheable} methods are populated once by {@code CacheWarmupRunner}
 * at startup and can be reused directly by controllers/services afterwards.
 */
@Service
@RequiredArgsConstructor
public class CatalogCacheService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductServiceInterface productService;

    @Cacheable(cacheNames = CacheConfig.CATEGORIES_CACHE, key = "'all'")
    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Cacheable(cacheNames = CacheConfig.PRODUCTS_CACHE, key = "'all'")
    @Transactional(readOnly = true)
    public List<ProductDto> getAllProducts() {
        // Convert while the transaction is open so lazy category/images load safely.
        return productService.getConvertedProducts(productRepository.findAll());
    }
}
