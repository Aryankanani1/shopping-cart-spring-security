package com.aryan.spring_security_demo.exception;

import com.aryan.spring_security_demo.repository.CategoryRepository;

public class CategoryNotFoundException extends RuntimeException{

    public CategoryNotFoundException(String message){
        super(message);
    }
}
