package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.request.LoginRequest;
import com.aryan.spring_security_demo.response.ApiResponse;
import com.aryan.spring_security_demo.response.JwtResponse;
import com.aryan.spring_security_demo.security.jwt.JwtUtils;
import com.aryan.spring_security_demo.security.user.UserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("${api.prefix}/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@RequestBody LoginRequest request){
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String token = jwtUtils.generateUserTokenFromUser(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            JwtResponse jwtResponse = new JwtResponse(userDetails.getId(), token);
            return ResponseEntity.ok(new ApiResponse("Login successful", jwtResponse));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(UNAUTHORIZED).body(new ApiResponse(e.getMessage(), null));
        }
    }
}
