package com.aryan.spring_security_demo.catalog;

import com.aryan.spring_security_demo.common.validation.NoProfanity;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A category as a client sends it: the body of {@code POST/PUT /categories}, and
 * the nested {@code category} of a product request (matched by name). Kept apart
 * from the {@link Category} entity so a request can't set the id or version.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @NoProfanity(message = "Category name contains disallowed words")
    private String name;
}
