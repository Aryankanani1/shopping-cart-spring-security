package com.aryan.spring_security_demo.catalog;

import com.aryan.spring_security_demo.common.exception.ResourceNotFoundException;

public class ImageNotFoundException extends ResourceNotFoundException{

    public ImageNotFoundException(String message) {
        super(message);
    }
}
