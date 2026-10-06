package com.aryan.spring_security_demo.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {

    // The 255-character limits match the varchar(255) columns.
    @NotBlank(message = "First name is required")
    @Size(max = 255, message = "First name must be at most 255 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 255, message = "Last name must be at most 255 characters")
    private String lastName;

}
