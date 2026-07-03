package com.aryan.spring_security_demo.request;

import com.aryan.spring_security_demo.model.Category;
import lombok.Data;

import java.math.BigDecimal;
@Data
public class ProductUpdateRequest {
    private Long id;
    private String name;
    private BigDecimal price;
    private String description;
    private String brand;
    private int inventory;
    private Category category;
}
