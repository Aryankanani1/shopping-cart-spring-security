package com.aryan.spring_security_demo.dto;

import com.aryan.spring_security_demo.model.Product;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemDto {
    private Long itemId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private ProductDto products;
}
