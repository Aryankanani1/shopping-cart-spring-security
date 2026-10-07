package com.aryan.spring_security_demo.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {

    // The 255-character limits match the varchar(255) columns.
    @NotBlank(message = "First name is required")
    @Size(max = 255, message = "First name must be at most 255 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 255, message = "Last name must be at most 255 characters")
    private String lastName;

    // @Email alone allows up to 320 characters.
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    // The maximum is BCrypt's: it only hashes the first 72 bytes, and Spring's
    // encoder rejects anything longer outright (UserService also checks bytes).
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 72, message = "Password must be 6-72 characters")
    private String password;
}
