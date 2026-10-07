package com.aryan.spring_security_demo.catalog;

import com.aryan.spring_security_demo.common.exception.ResourceNotFoundException;


public class ProductNotFoundException extends ResourceNotFoundException{

    public ProductNotFoundException(String message){
        super(message);
    }
}
