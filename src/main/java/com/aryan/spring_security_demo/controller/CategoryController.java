package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.category.CategoryServiceInterface;
import com.aryan.spring_security_demo.exception.CategoryNotFoundException;
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

import static org.springframework.http.HttpStatus.*;

@RequiredArgsConstructor
@RestController
@Validated
@RequestMapping("${api.prefix}/categories")
public class CategoryController {

    private final CategoryServiceInterface categoryServiceInterface;

    @GetMapping
    public ResponseEntity<ApiResponse> getAllCategories(){

        try {
            List<Category> categories = categoryServiceInterface.getAllCategories();
            return ResponseEntity.ok(new ApiResponse("success!", categories));
        }catch (Exception e){
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error:",INTERNAL_SERVER_ERROR));
        }
    }


    @PostMapping
    public ResponseEntity<ApiResponse> addCategory(@Valid @RequestBody Category name){
        try {
            Category category = categoryServiceInterface.addCategory(name);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}").buildAndExpand(category.getId()).toUri();
            return ResponseEntity.created(location).body(new ApiResponse("Success!", category));
        }
        catch (Exception e){
            return ResponseEntity.status(CONFLICT)
                    .body(new ApiResponse(e.getMessage(),null));
        }
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse> getCategoryById(@PathVariable Long id){
        try{
           Category category = categoryServiceInterface.getCategoryById(id);
            return ResponseEntity.ok(new ApiResponse("Success!",category));
        }catch (Exception e){
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Category does not Exists",null));
        }
    }

    @GetMapping(params = "name")
    public ResponseEntity<ApiResponse> getCategoryByName(@RequestParam String name){
        try{
            Category category = categoryServiceInterface.getCategoryByName(name);
            return ResponseEntity.ok(new ApiResponse("Success!",category));
        }catch (CategoryNotFoundException e){
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Category does not Exists",null));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteCategoryById(@PathVariable Long id){
        try{
             categoryServiceInterface.deleteCategoryById(id);
            return ResponseEntity.noContent().build();
        }catch (CategoryNotFoundException e){
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Category does not Exists",null));
        }
    }


    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateCategoryId(@PathVariable Long id,@Valid @RequestBody Category category){
        try{
           Category updatedCategory =  categoryServiceInterface.updateCategory(category,id);
            return ResponseEntity.ok(new ApiResponse("Found!",updatedCategory));
        }catch (CategoryNotFoundException e){
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse(e.getMessage(),null));
        }
    }
}
