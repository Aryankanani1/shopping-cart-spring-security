package com.aryan.spring_security_demo.service.category;
import com.aryan.spring_security_demo.dto.CategoryDto;
import com.aryan.spring_security_demo.exception.AlreadyExistsException;
import com.aryan.spring_security_demo.exception.CategoryNotFoundException;
import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
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

    @Override
    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Override
    @Transactional
    public Category addCategory(Category category) {
        // check the category if it is existing or not if exist we can't
        // create those categories, and
        // if not exists we can create those categories

        if(categoryRepository.existsByName(category.getName())){
            throw new AlreadyExistsException("Category already exists");
        }
        return categoryRepository.save(category);

    }

    @Override
    @Transactional
    public Category updateCategory(Category category,Long id) {
     return Optional.ofNullable(getCategoryById(id)).map(oldCategory -> {
           oldCategory.setName(category.getName());
           return categoryRepository.save(oldCategory);
       })
             .orElseThrow(() -> new CategoryNotFoundException("category not found exception"));
    }



    @Override
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
