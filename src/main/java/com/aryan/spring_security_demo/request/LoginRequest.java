package com.aryan.spring_security_demo.request;

import lombok.Data;

@Data
public class LoginRequest {

    private String email;
    private String password;
}
