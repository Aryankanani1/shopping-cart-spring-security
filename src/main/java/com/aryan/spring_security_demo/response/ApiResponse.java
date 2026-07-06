package com.aryan.spring_security_demo.response;

import lombok.AllArgsConstructor;
import lombok.Data;
@AllArgsConstructor
@Data
public class ApiResponse {

    private String message;
    private Object data;


}
