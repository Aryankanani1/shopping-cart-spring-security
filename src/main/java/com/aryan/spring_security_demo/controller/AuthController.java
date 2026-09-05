package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.request.LoginRequest;
import com.aryan.spring_security_demo.request.RefreshTokenRequest;
import com.aryan.spring_security_demo.response.ApiResponse;
import com.aryan.spring_security_demo.response.JwtResponse;
import com.aryan.spring_security_demo.security.jwt.JwtUtils;
import com.aryan.spring_security_demo.security.user.UserDetails;
import com.aryan.spring_security_demo.service.auth.RefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix}/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@RequestBody LoginRequest request) {
        // Bad credentials throw BadCredentialsException, which the global handler
        // maps to 401 — no try/catch here, consistent with every other endpoint.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtUtils.generateUserTokenFromUser(authentication);
        String refreshToken = refreshTokenService.issueFor(userDetails.getId());

        JwtResponse jwtResponse = new JwtResponse(userDetails.getId(), accessToken, refreshToken);
        return ResponseEntity.ok(new ApiResponse<>("Login successful", jwtResponse));
    }

    /**
     * Exchange a valid refresh token for a new access token. The refresh token is
     * rotated (the old one is revoked and a new one returned), so each token is
     * single-use and replay of a spent token is detectable.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<?>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(request.getRefreshToken());
        String accessToken = jwtUtils.generateTokenFromUserDetails(rotated.principal());

        JwtResponse jwtResponse = new JwtResponse(
                rotated.principal().getId(), accessToken, rotated.rawRefreshToken());
        return ResponseEntity.ok(new ApiResponse<>("Token refreshed", jwtResponse));
    }

    /**
     * Revoke the refresh token, ending the session server-side. The access token
     * remains technically valid until it expires shortly after, but no new one can
     * be minted. Idempotent — an unknown/already-revoked token still returns 200.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.revoke(request.getRefreshToken());
        return ResponseEntity.ok(new ApiResponse<>("Logged out", null));
    }
}
