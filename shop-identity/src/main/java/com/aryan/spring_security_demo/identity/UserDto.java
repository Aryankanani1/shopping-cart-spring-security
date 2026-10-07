package com.aryan.spring_security_demo.identity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

// The account only. Order history grows without bound, so it is served paginated
// (GET /orders); the cart is served by GET /carts/mine.
@Data
@JsonPropertyOrder({"id", "firstName", "lastName", "email"})
public class UserDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
}
