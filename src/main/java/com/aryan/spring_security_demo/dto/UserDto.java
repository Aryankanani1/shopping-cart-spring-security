package com.aryan.spring_security_demo.dto;

import com.aryan.spring_security_demo.model.Order;
import lombok.Data;

import java.util.List;

@Data
public class UserDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;

   private List<OrderDto> orders;
   private CartDto carts;

}
