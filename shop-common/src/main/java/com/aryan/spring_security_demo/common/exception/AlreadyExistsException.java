package com.aryan.spring_security_demo.common.exception;

public class AlreadyExistsException extends ConflictException {
    public AlreadyExistsException(String message) {
        super("Resource already exists", message);
    }

    @Override
    public synchronized Throwable fillInStackTrace(){
      return this;
    }
}

