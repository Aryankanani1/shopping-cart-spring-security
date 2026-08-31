package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.service.category.CategoryServiceInterface;
import com.aryan.spring_security_demo.dto.CategoryDto;
import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RequiredArgsConstructor
@RestController
@Validated
@RequestMapping("${api.prefix}/categories")
public class CategoryController {

    private final CategoryServiceInterface categoryServiceInterface;

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getAllCategories(){
        List<CategoryDto> categories = categoryServiceInterface.getAllCategories().stream()
                .map(categoryServiceInterface::convertToDto)
                .toList();
        return ResponseEntity.ok(new ApiResponse<>("success!", categories));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> addCategory(@Valid @RequestBody Category name){
        Category category = categoryServiceInterface.addCategory(name);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(category.getId()).toUri();
        return ResponseEntity.created(location)
                .body(new ApiResponse<>("Success!", categoryServiceInterface.convertToDto(category)));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<?>> getCategoryById(@PathVariable Long id){
        Category category = categoryServiceInterface.getCategoryById(id);
        return ResponseEntity.ok(new ApiResponse<>("Success!", categoryServiceInterface.convertToDto(category)));
    }

    @GetMapping(params = "name")
    public ResponseEntity<ApiResponse<?>> getCategoryByName(@RequestParam String name){
        Category category = categoryServiceInterface.getCategoryByName(name);
        CategoryDto dto = category == null ? null : categoryServiceInterface.convertToDto(category);
        return ResponseEntity.ok(new ApiResponse<>("Success!", dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> deleteCategoryById(@PathVariable Long id){
        categoryServiceInterface.deleteCategoryById(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> updateCategoryId(@PathVariable Long id, @Valid @RequestBody Category category){
        Category updatedCategory = categoryServiceInterface.updateCategory(category, id);
        return ResponseEntity.ok(new ApiResponse<>("Found!", categoryServiceInterface.convertToDto(updatedCategory)));
    }
}
