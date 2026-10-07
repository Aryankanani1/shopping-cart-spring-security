package com.aryan.spring_security_demo.identity;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;

/**
 * The optional first admin account ({@code app.bootstrap.admin.*}, set with the
 * {@code ADMIN_EMAIL} and {@code ADMIN_PASSWORD} env vars). Outside dev there is
 * no other way to get an admin: sign-up only makes customers, and the
 * {@link DevDataSeeder} admins exist only under the dev profile. See
 * {@link AdminBootstrap} for what happens at startup.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.bootstrap.admin")
public class AdminBootstrapProperties {

    private static final int MIN_PASSWORD_LENGTH = 12;
    /** BCrypt hashes only this many bytes, and the encoder rejects longer input. */
    private static final int MAX_PASSWORD_BYTES = 72;

    /** The admin's email. Blank (the default) turns the bootstrap off. */
    @Email(message = "ADMIN_EMAIL must be a valid email address")
    private String email = "";

    /** Only needed to create the account; an existing account keeps its password. */
    private String password = "";

    public boolean isEnabled() {
        return email != null && !email.isBlank();
    }

    /** Longer than a customer's 6-character minimum: this account runs the shop. */
    @AssertTrue(message = "ADMIN_PASSWORD must be 12-72 characters")
    public boolean isPasswordUsable() {
        if (password == null || password.isEmpty()) {
            return true;  // only required when the account is created (AdminBootstrap checks)
        }
        return password.length() >= MIN_PASSWORD_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }
}
