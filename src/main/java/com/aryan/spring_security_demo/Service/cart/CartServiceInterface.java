package com.aryan.spring_security_demo.Service.cart;

import com.aryan.spring_security_demo.dto.CartDto;
import com.aryan.spring_security_demo.model.Cart;
import com.aryan.spring_security_demo.model.User;

import java.math.BigDecimal;

public interface CartServiceInterface {
    Cart getCart(Long id);

    /** Read a cart as a fully-populated DTO — safe to serialize with open-in-view off. */
    CartDto getCartDto(Long id);

    void clearCart(Long id);
    BigDecimal getTotalPrice(Long id);

    Cart initializeNewCart(User user);

    Cart getCartByUserId(Long userId);
}
