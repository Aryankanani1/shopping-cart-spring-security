package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.category.CategoryServiceInterface;
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
    public ResponseEntity<ApiResponse> getAllCategories(){
        List<Category> categories = categoryServiceInterface.getAllCategories();
        return ResponseEntity.ok(new ApiResponse("success!", categories));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> addCategory(@Valid @RequestBody Category name){
        Category category = categoryServiceInterface.addCategory(name);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(category.getId()).toUri();
        return ResponseEntity.created(location).body(new ApiResponse("Success!", category));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse> getCategoryById(@PathVariable Long id){
        Category category = categoryServiceInterface.getCategoryById(id);
        return ResponseEntity.ok(new ApiResponse("Success!", category));
    }

    @GetMapping(params = "name")
    public ResponseEntity<ApiResponse> getCategoryByName(@RequestParam String name){
        Category category = categoryServiceInterface.getCategoryByName(name);
        return ResponseEntity.ok(new ApiResponse("Success!", category));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteCategoryById(@PathVariable Long id){
        categoryServiceInterface.deleteCategoryById(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateCategoryId(@PathVariable Long id, @Valid @RequestBody Category category){
        Category updatedCategory = categoryServiceInterface.updateCategory(category, id);
        return ResponseEntity.ok(new ApiResponse("Found!", updatedCategory));
    }
}
