package com.aryan.spring_security_demo.identity;

import com.aryan.spring_security_demo.common.exception.ResourceNotFoundException;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(String message){
        super(message);
    }

}
