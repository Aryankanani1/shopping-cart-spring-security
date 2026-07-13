package com.aryan.spring_security_demo.exception;

public class UserNoFoundException extends RuntimeException {

    public UserNoFoundException(String message){
        super(message);
    }

}
