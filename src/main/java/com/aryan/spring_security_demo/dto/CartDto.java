package com.aryan.spring_security_demo.dto;

import com.aryan.spring_security_demo.model.CartItem;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

@Data
public class CartDto {

    private Long cartId;
    private Set<CartItemDto> cartItems;
    private BigDecimal totalAmount;
}
