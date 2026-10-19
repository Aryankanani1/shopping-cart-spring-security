package com.aryan.spring_security_demo.identity;

import com.aryan.spring_security_demo.identity.security.jwt.JwtKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * The public keys that verify this application's access tokens, as a JWK Set
 * (RFC 7517). Another service can check a token with them, picking the key the
 * token's {@code kid} names, without being able to make one: only this
 * application holds the private key.
 *
 * <p>Under the API prefix, so the proxy that serves the API serves it too, and
 * outside {@code /auth}, so the rate limiter doesn't hold back a service fetching
 * the keys. Not wrapped in ApiResponse: a JWK Set has a standard shape,
 * {@code {"keys": [...]}}, that libraries read as it is.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtKeys jwtKeys;

    // Cached for 5 minutes: a new key is published before it signs anything, and an
    // old one stays published for a whole access-token lifetime after it stops.
    @GetMapping("${api.prefix}/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(jwtKeys.publicKeys().toJSONObject());
    }
}
