package com.aryan.spring_security_demo.identity;

import com.aryan.spring_security_demo.cart.CartDto;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

// No order history here: it grows without bound, so it is only served paginated
// (GET /orders). The cart is bounded and the storefront reads it from here.
@Data
@JsonPropertyOrder({"id", "firstName", "lastName", "email", "cart"})
public class UserDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;

   private CartDto cart;

}
