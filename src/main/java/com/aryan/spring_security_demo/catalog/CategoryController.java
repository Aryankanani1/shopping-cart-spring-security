package com.aryan.spring_security_demo.catalog;

import com.aryan.spring_security_demo.common.web.ApiResponse;
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
        List<CategoryDto> categories = categoryServiceInterface.getAllCategoryDtos();
        return ResponseEntity.ok(new ApiResponse<>("success!", categories));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> addCategory(@Valid @RequestBody CategoryRequest request){
        Category category = categoryServiceInterface.addCategory(request);
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
    public ResponseEntity<ApiResponse<?>> updateCategoryId(@PathVariable Long id, @Valid @RequestBody CategoryRequest request){
        Category updatedCategory = categoryServiceInterface.updateCategory(request, id);
        return ResponseEntity.ok(new ApiResponse<>("Found!", categoryServiceInterface.convertToDto(updatedCategory)));
    }
}
