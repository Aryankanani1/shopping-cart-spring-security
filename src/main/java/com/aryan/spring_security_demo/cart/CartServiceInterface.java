package com.aryan.spring_security_demo.cart;

import com.aryan.spring_security_demo.identity.User;

import java.math.BigDecimal;

public interface CartServiceInterface {
    Cart getCart(Long id);

    /** Read a cart as a fully-populated DTO — safe to serialize with open-in-view off. */
    CartDto getCartDto(Long id);

    /** The signed-in user's cart; 404 until their first add-to-cart creates it. */
    CartDto getMyCartDto();

    void clearCart(Long id);
    BigDecimal getTotalPrice(Long id);

    Cart initializeNewCart(User user);

    Cart getCartByUserId(Long userId);
}
