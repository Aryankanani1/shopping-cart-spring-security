package com.aryan.spring_security_demo.identity;

import com.aryan.spring_security_demo.cart.CartDto;
import com.aryan.spring_security_demo.order.OrderDto;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.List;

@Data
@JsonPropertyOrder({"id", "firstName", "lastName", "email", "orders","cart"})
public class UserDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;

   private List<OrderDto> orders;
   private CartDto cart;

}
