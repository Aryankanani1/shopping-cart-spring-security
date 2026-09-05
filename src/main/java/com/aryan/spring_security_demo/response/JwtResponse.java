package com.aryan.spring_security_demo.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JwtResponse {

    private Long id;
    /** Short-lived access token (JWT) sent as the Bearer credential on each request. */
    private String token;
    /** Longer-lived, revocable refresh token; exchanged at /auth/refresh for a new access token. */
    private String refreshToken;
}
