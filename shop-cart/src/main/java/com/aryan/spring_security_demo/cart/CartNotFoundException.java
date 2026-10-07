package com.aryan.spring_security_demo.cart;

import com.aryan.spring_security_demo.common.exception.ResourceNotFoundException;

public class CartNotFoundException extends ResourceNotFoundException{
    public CartNotFoundException(String message) {
        super(message);
    }
}
