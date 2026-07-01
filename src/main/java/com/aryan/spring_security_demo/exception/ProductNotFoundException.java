package com.aryan.spring_security_demo.exception;

import lombok.AllArgsConstructor;

public class ProductNotFoundException extends RuntimeException{

    public ProductNotFoundException(String message){
        super(message);
    }
}
