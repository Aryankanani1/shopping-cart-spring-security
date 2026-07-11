package com.aryan.spring_security_demo.Service.cartItem;

import com.aryan.spring_security_demo.model.CartItem;

public interface CartItemServiceInterface {
     void addItemToCart(Long CartId, Long productId, Integer quantity);

     void removeItemFromCart(Long cartId, Long productId);

     void updateItemQuantity(Long cartId, Long productId, int quantity);
     CartItem getCartItem(Long cartId, Long productId);
}
