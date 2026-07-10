package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.cart.CartServiceInterface;
import com.aryan.spring_security_demo.exception.CartNotFoundException;
import com.aryan.spring_security_demo.model.Cart;
import com.aryan.spring_security_demo.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RequiredArgsConstructor
@RestController
@RequestMapping("${api.prefix}/carts")
public class CartController {

    private final CartServiceInterface cartServiceInterface;

    @GetMapping("/{cartId}/my-cart")
    public ResponseEntity<ApiResponse> getCart(@PathVariable Long cartId){
        try {
            Cart cart = cartServiceInterface.getCart(cartId);
            return ResponseEntity.ok(new ApiResponse("Success", cart));
        }catch (CartNotFoundException e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @DeleteMapping("/{cartId}/clear")
    public ResponseEntity<ApiResponse> clearCart(@PathVariable Long cartId){
        try {
            cartServiceInterface.clearCart(cartId);
            return ResponseEntity.ok(new ApiResponse("cart cleared successfully", null));
        }catch (CartNotFoundException e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @GetMapping("/{cartId}/cart/total-price")
    public ResponseEntity<ApiResponse> getTotalAmount(@PathVariable Long cartId){
        try {
            BigDecimal totalPrice = cartServiceInterface.getTotalPrice(cartId);
            return ResponseEntity.ok(new ApiResponse("total price", totalPrice));
        }catch (CartNotFoundException e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
    }

}
