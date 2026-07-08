package com.aryan.spring_security_demo.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductDto {
    private Long id;
    private String name;
    private String brand;
    private BigDecimal price;
    private String description;
    private int inventory;
    private String categoryName;
    private List<ImageDto> images;
}
