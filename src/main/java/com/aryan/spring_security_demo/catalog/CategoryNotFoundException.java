package com.aryan.spring_security_demo.catalog;

import com.aryan.spring_security_demo.common.exception.ResourceNotFoundException;


public class CategoryNotFoundException extends ResourceNotFoundException{

    public CategoryNotFoundException(String message){
        super(message);
    }
}
