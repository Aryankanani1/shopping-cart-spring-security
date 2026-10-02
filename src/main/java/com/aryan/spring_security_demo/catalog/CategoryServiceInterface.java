package com.aryan.spring_security_demo.catalog;


import java.util.List;

public interface CategoryServiceInterface {
    Category getCategoryById(Long id);
    Category getCategoryByName(String name);
    /** Every category, served from the {@code categories} cache. */
    List<CategoryDto> getAllCategoryDtos();

    /** The category with this name, created if there isn't one yet. */
    Category findOrCreate(String name);

    Category addCategory(CategoryRequest request);

    Category updateCategory(CategoryRequest request, Long id);
    void deleteCategoryById(Long id);

    CategoryDto convertToDto(Category category);
}
