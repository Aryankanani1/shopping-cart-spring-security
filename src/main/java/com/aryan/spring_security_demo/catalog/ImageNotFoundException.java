package com.aryan.spring_security_demo.catalog;

public class ImageNotFoundException extends RuntimeException{

    public ImageNotFoundException(String message) {
        super(message);
    }
}
