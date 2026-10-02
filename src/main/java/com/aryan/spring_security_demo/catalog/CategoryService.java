package com.aryan.spring_security_demo.catalog;
import com.aryan.spring_security_demo.common.config.CacheConfig;
import com.aryan.spring_security_demo.common.exception.AlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoryService implements CategoryServiceInterface{

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("category not found exception"));
    }

    @Override
    @Transactional(readOnly = true)
    public Category getCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }

    /**
     * Cached, since the storefront reads the list on most pages and it rarely
     * changes. Every method below that creates, renames or deletes a category
     * clears the cache; the clear applies when its transaction commits (see
     * CacheConfig). DTOs are cached, not entities, so nothing lazy or mutable
     * outlives the transaction.
     */
    @Override
    @Cacheable(cacheNames = CacheConfig.CATEGORIES_CACHE, key = "'all'")
    @Transactional(readOnly = true)
    public List<CategoryDto> getAllCategoryDtos() {
        return categoryRepository.findAll().stream().map(this::convertToDto).toList();
    }

    // Clears the cache even when the category already exists: cheap, and only on
    // admin product writes.
    @Override
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES_CACHE, allEntries = true)
    @Transactional
    public Category findOrCreate(String name) {
        return Optional.ofNullable(categoryRepository.findByName(name))
                .orElseGet(() -> categoryRepository.save(new Category(name)));
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES_CACHE, allEntries = true)
    @Transactional
    public Category addCategory(CategoryRequest request) {
        // check the category if it is existing or not if exist we can't
        // create those categories, and
        // if not exists we can create those categories

        if(categoryRepository.existsByName(request.getName())){
            throw new AlreadyExistsException("Category already exists");
        }
        return categoryRepository.save(new Category(request.getName()));

    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES_CACHE, allEntries = true)
    @Transactional
    public Category updateCategory(CategoryRequest request, Long id) {
        Category category = getCategoryById(id);
        // Same rule as addCategory: names are unique (the database enforces it
        // too), since lookups by name expect at most one match.
        if (categoryRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new AlreadyExistsException("Category already exists");
        }
        category.setName(request.getName());
        return categoryRepository.save(category);
    }



    @Override
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES_CACHE, allEntries = true)
    @Transactional
    public void deleteCategoryById(Long id) {
categoryRepository.findById(id).ifPresentOrElse(categoryRepository::delete,() -> {

   throw new CategoryNotFoundException("category not found exception");

});

    }

    @Override
    public CategoryDto convertToDto(Category category) {
        CategoryDto dto = new CategoryDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        return dto;
    }
}
