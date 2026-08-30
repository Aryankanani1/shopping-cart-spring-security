package com.aryan.spring_security_demo.Service.category;

import com.aryan.spring_security_demo.dto.CategoryDto;
import com.aryan.spring_security_demo.model.Category;

import java.util.List;

public interface CategoryServiceInterface {
    Category getCategoryById(Long id);
    Category getCategoryByName(String name);
    List<Category> getAllCategories();

    Category addCategory(Category category);

    Category updateCategory(Category category,Long id);
    void deleteCategoryById(Long id);

    CategoryDto convertToDto(Category category);
}
