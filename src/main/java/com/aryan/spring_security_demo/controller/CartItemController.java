package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.cartItem.CartItemServiceInterface;
import com.aryan.spring_security_demo.exception.CartNotFoundException;
import com.aryan.spring_security_demo.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RequiredArgsConstructor
@RestController
@RequestMapping("${api.prefix}/cartItems")
public class CartItemController {

    private final CartItemServiceInterface cartItemServiceInterface;


    public ResponseEntity<ApiResponse> addItemToCart(
            @RequestParam Long cartId,
            @RequestParam Long productId,
            @RequestParam Integer quantity){

        try {
            cartItemServiceInterface.addItemToCart(cartId, productId, quantity);
            return ResponseEntity.ok(new ApiResponse("add item successfully!", null));
        }
        catch (CartNotFoundException e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
    }





}
