package com.aryan.spring_security_demo.request;
import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.validation.NoProfanity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
@Data
public class AddProductRequest {
    private Long id;

    @NotBlank(message = "Product name is required")
    @NoProfanity(message = "Product name contains disallowed words")
    private String name;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than zero")
    private BigDecimal price;

    private String description;

    @NotBlank(message = "Brand is required")
    private String brand;

    @PositiveOrZero(message = "Inventory cannot be negative")
    private int inventory;

    // Cascade validation into the nested category (@NotBlank/@NoProfanity on name).
    @NotNull(message = "Category is required")
    @Valid
    private Category category;
}
