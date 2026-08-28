package com.aryan.spring_security_demo.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserUpdateRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

}
