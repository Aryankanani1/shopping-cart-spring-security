package com.aryan.spring_security_demo.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** A new product: the shared fields (ProductRequestFields) and a required category. */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AddProductRequest extends ProductRequestFields {
    // Cascade validation into the nested category (@NotBlank/@NoProfanity on name).
    @NotNull(message = "Category is required")
    @Valid
    private CategoryRequest category;
}
