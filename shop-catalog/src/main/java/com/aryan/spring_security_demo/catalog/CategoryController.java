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

    /**
     * All categories, or with {@code ?name=} the one of that name (404 if there is
     * none). One handler, not a second one mapped with {@code params = "name"}: the
     * spec has one GET per path, and springdoc documented the pair as one operation
     * that requires a name.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<?>> getCategories(@RequestParam(required = false) String name){
        if (name == null) {
            List<CategoryDto> categories = categoryServiceInterface.getAllCategoryDtos();
            return ResponseEntity.ok(new ApiResponse<>("success!", categories));
        }
        Category category = categoryServiceInterface.getCategoryByName(name);
        return ResponseEntity.ok(new ApiResponse<>("Success!", categoryServiceInterface.convertToDto(category)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> addCategory(@Valid @RequestBody CategoryRequest request){
        Category category = categoryServiceInterface.addCategory(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(category.getId()).toUri();
        return ResponseEntity.created(location)
                .body(new ApiResponse<>("Success!", categoryServiceInterface.convertToDto(category)));
    }

    // Any id, as for PUT and DELETE: with only digits allowed, a negative id matched
    // those two instead and got a 405.
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getCategoryById(@PathVariable Long id){
        Category category = categoryServiceInterface.getCategoryById(id);
        return ResponseEntity.ok(new ApiResponse<>("Success!", categoryServiceInterface.convertToDto(category)));
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
