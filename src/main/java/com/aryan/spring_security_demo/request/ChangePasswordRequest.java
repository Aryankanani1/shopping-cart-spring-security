package com.aryan.spring_security_demo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body of {@code PUT /auth/password}. There is no user id: the change always
 * applies to the authenticated caller, so there is no id to guess (no IDOR).
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    // Same minimum as registration. The maximum is BCrypt's: it only hashes the
    // first 72 bytes, and Spring's encoder rejects anything longer outright.
    @NotBlank(message = "New password is required")
    @Size(min = 6, max = 72, message = "New password must be 6-72 characters")
    private String newPassword;
}
