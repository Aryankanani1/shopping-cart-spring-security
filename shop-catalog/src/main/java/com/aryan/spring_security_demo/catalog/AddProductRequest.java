package com.aryan.spring_security_demo.catalog;
import com.aryan.spring_security_demo.common.validation.NoProfanity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
@Data
public class AddProductRequest {
    private Long id;

    // The 255-character limits match the varchar(255) columns.
    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must be at most 255 characters")
    @NoProfanity(message = "Product name contains disallowed words")
    private String name;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than zero")
    private BigDecimal price;

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    @NotBlank(message = "Brand is required")
    @Size(max = 255, message = "Brand must be at most 255 characters")
    private String brand;

    @PositiveOrZero(message = "Inventory cannot be negative")
    private int inventory;

    // Cascade validation into the nested category (@NotBlank/@NoProfanity on name).
    @NotNull(message = "Category is required")
    @Valid
    private CategoryRequest category;
}
