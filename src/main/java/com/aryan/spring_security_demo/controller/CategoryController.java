package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.category.CategoryServiceInterface;
import com.aryan.spring_security_demo.exception.CategoryNotFoundException;
import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("${api.prefix}/categories")
public class CategoryController {

    private final CategoryServiceInterface categoryServiceInterface;

    @GetMapping("/all")
    public ResponseEntity<ApiResponse> getAllCategories(){

        try {
            List<Category> categories = categoryServiceInterface.getAllCategories();
            return ResponseEntity.ok(new ApiResponse("success!", categories));
        }catch (Exception e){
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error:",INTERNAL_SERVER_ERROR));
        }
    }


    @PostMapping("/add")
    public ResponseEntity<ApiResponse> addCategory(@RequestBody Category name){
        try {
            Category category = categoryServiceInterface.addCategory(name);
            return ResponseEntity.ok(new ApiResponse("Success!", category));
        }
        catch (Exception e){
            return ResponseEntity.status(CONFLICT)
                    .body(new ApiResponse(e.getMessage(),null));
        }
    }

    @GetMapping("/category/{id}/category")
    public ResponseEntity<ApiResponse> getCategoryById(@PathVariable Long id){
        try{
           Category category = categoryServiceInterface.getCategoryById(id);
            return ResponseEntity.ok(new ApiResponse("Success!",category));
        }catch (Exception e){
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Category does not Exists",null));
        }
    }

    @GetMapping("category/{name}/category")
    public ResponseEntity<ApiResponse> getCategoryByName(@PathVariable String name){
        try{
            Category category = categoryServiceInterface.getCategoryByName(name);
            return ResponseEntity.ok(new ApiResponse("Success!",category));
        }catch (CategoryNotFoundException e){
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Category does not Exists",null));
        }
    }

    @DeleteMapping("category/{id}/delete")
    public ResponseEntity<ApiResponse> deleteCategoryById(@PathVariable Long id){
        try{
             categoryServiceInterface.deleteCategoryById(id);
            return ResponseEntity.ok(new ApiResponse("Found!",null));
        }catch (CategoryNotFoundException e){
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Category does not Exists",null));
        }
    }


    @PutMapping("category/{id}/update")
    public ResponseEntity<ApiResponse> updateCategoryId(@PathVariable Long id,@RequestBody Category category){
        try{
           Category updatedCategory =  categoryServiceInterface.updateCategory(category,id);
            return ResponseEntity.ok(new ApiResponse("Found!",updatedCategory));
        }catch (CategoryNotFoundException e){
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse(e.getMessage(),null));
        }
    }
}
