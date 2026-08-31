package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.cart.CartServiceInterface;
import com.aryan.spring_security_demo.Service.cartItem.CartItemServiceInterface;
import com.aryan.spring_security_demo.Service.user.UserServiceInterface;
import com.aryan.spring_security_demo.model.Cart;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.CREATED;

@RequiredArgsConstructor
@RestController
@RequestMapping("${api.prefix}/cartItems")
public class CartItemController {

    private final CartItemServiceInterface cartItemServiceInterface;
    private final CartServiceInterface cartServiceInterface;
    private final UserServiceInterface userServiceInterface;


    @PostMapping
    public ResponseEntity<ApiResponse<?>> addItemToCart(
            @RequestParam Long productId,
            @RequestParam Integer quantity){

        User user = userServiceInterface.getAuthenticatedUser();
        Cart cart = cartServiceInterface.initializeNewCart(user);

        cartItemServiceInterface.addItemToCart(cart.getId(), productId, quantity);
        return ResponseEntity.status(CREATED).body(new ApiResponse<>("add item successfully!", null));
    }


    @DeleteMapping("/cart/{cartId}/product/{productId}")
    public ResponseEntity<ApiResponse<?>> removeItemFromCart(
            @PathVariable Long cartId,
            @PathVariable Long productId
    ){
        cartItemServiceInterface.removeItemFromCart(cartId, productId);
        return ResponseEntity.noContent().build();
    }


    @PutMapping("/cart/{cartId}/item/{itemId}")
    public ResponseEntity<ApiResponse<?>> updateItemQuantity(
            @PathVariable Long cartId,
            @PathVariable Long itemId,
            @RequestParam Integer quantity
    ) {
        cartItemServiceInterface.updateItemQuantity(cartId, itemId, quantity);
        return ResponseEntity.ok(new ApiResponse<>("item updated successfully", null));
    }
}
