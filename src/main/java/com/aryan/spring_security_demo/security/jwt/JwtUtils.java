package com.aryan.spring_security_demo.security.jwt;

import com.aryan.spring_security_demo.config.AuthTokenProperties;
import com.aryan.spring_security_demo.security.user.UserDetails;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final AuthTokenProperties authTokenProperties;

    public String generateUserTokenFromUser(Authentication authentication){
        return generateTokenFromUserDetails((UserDetails) authentication.getPrincipal());
    }

    /**
     * Mint an access token straight from a principal. Used both at login (from the
     * authenticated {@code Authentication}) and on refresh, where the principal is
     * rebuilt from the refresh token's user rather than a login.
     */
    public String generateTokenFromUserDetails(UserDetails userPrinciple){
        List<String> roles = userPrinciple.getAuthorities()
                .stream().map(GrantedAuthority::getAuthority).toList();

        return Jwts.builder()
                .subject(userPrinciple.getEmail())
                .claim("id",userPrinciple.getId())
                .claim("roles",roles)
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + authTokenProperties.getExpirationInMils()))
                .signWith(key())
                .compact();
    }

    private SecretKey key(){
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(authTokenProperties.getJwtSecret()));
    }

    String getUserNameFromToken(String token){
        return Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(token);
            return true;
        }catch (Exception e){
            throw new JwtException(e.getMessage());
        }
    }

}
