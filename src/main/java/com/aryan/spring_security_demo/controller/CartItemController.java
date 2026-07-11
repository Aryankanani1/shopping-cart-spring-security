package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.cart.CartServiceInterface;
import com.aryan.spring_security_demo.Service.cartItem.CartItemServiceInterface;
import com.aryan.spring_security_demo.exception.CartNotFoundException;
import com.aryan.spring_security_demo.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RequiredArgsConstructor
@RestController
@RequestMapping("${api.prefix}/cartItems")
public class CartItemController {

    private final CartItemServiceInterface cartItemServiceInterface;
    private final CartServiceInterface cartServiceInterface;


    @PostMapping("/item/add")
    public ResponseEntity<ApiResponse> addItemToCart(
            @RequestParam(required = false) Long cartId,
            @RequestParam Long productId,
            @RequestParam Integer quantity){

        try {
            if(cartId == null){
                cartId = cartServiceInterface.initializeNewCart();
            }
            cartItemServiceInterface.addItemToCart(cartId, productId, quantity);
            return ResponseEntity.ok(new ApiResponse("add item successfully!", null));
        }
        catch (CartNotFoundException e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
    }


    @DeleteMapping("cart/{cartId}/item/{productId}/remove")
    public ResponseEntity<ApiResponse> removeItemFromCart(
            @PathVariable Long cartId,
            @PathVariable Long productId
    ){

        try{
         cartItemServiceInterface.removeItemFromCart(cartId,productId);
         return ResponseEntity.ok(new ApiResponse("item removed successfully",null));
    }catch (CartNotFoundException e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
        }


        @PutMapping("/cart/{cartId}/item/{itemId}/update")
        public ResponseEntity<ApiResponse> updateItemQuantity(
                @PathVariable Long cartId,
                @PathVariable Long itemId,
                @RequestParam Integer quantity
        ) {
            try{
            cartItemServiceInterface.updateItemQuantity(cartId, itemId, quantity);
            return ResponseEntity.ok(new ApiResponse("item updated successfully",null));
        }catch (CartNotFoundException e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
            }
        }




}
