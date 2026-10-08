package com.aryan.spring_security_demo.catalog;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** Changes to a product: the shared fields (ProductRequestFields) and, optionally, its category. */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProductUpdateRequest extends ProductRequestFields {
    // Cascade validation into the nested category if one is supplied.
    @Valid
    private CategoryRequest category;
}
