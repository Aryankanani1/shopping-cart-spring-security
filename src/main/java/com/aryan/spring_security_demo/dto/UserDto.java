package com.aryan.spring_security_demo.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.List;

@Data
@JsonPropertyOrder({"id", "firstName", "lastName", "email", "cart", "orders"})
public class UserDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;

   private List<OrderDto> orders;
   private CartDto cart;

}
