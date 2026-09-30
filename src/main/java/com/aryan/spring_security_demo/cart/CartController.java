package com.aryan.spring_security_demo.cart;

import com.aryan.spring_security_demo.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RequiredArgsConstructor
@RestController
@RequestMapping("${api.prefix}/carts")
public class CartController {

    private final CartServiceInterface cartServiceInterface;

    @GetMapping("/{cartId}")
    public ResponseEntity<ApiResponse<?>> getCart(@PathVariable Long cartId){
        CartDto cart = cartServiceInterface.getCartDto(cartId);
        return ResponseEntity.ok(new ApiResponse<>("Success", cart));
    }

    @DeleteMapping("/{cartId}/items")
    public ResponseEntity<ApiResponse<?>> clearCart(@PathVariable Long cartId){
        cartServiceInterface.clearCart(cartId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{cartId}/total-price")
    public ResponseEntity<ApiResponse<?>> getTotalAmount(@PathVariable Long cartId){
        BigDecimal totalPrice = cartServiceInterface.getTotalPrice(cartId);
        return ResponseEntity.ok(new ApiResponse<>("total price", totalPrice));
    }

}
