package com.aryan.spring_security_demo.identity.security.jwt;

import com.aryan.spring_security_demo.identity.AuthTokenProperties;
import com.aryan.spring_security_demo.identity.security.user.ShopUserDetails;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final AuthTokenProperties authTokenProperties;
    private final Clock clock;

    public String generateUserTokenFromUser(Authentication authentication){
        if (!(authentication.getPrincipal() instanceof ShopUserDetails user)) {
            throw new IllegalStateException("Not a signed-in user's authentication: no ShopUserDetails principal");
        }
        return generateTokenFromUserDetails(user);
    }

    /**
     * Mint an access token straight from a principal. Used both at login (from the
     * authenticated {@code Authentication}) and on refresh, where the principal is
     * rebuilt from the refresh token's user rather than a login.
     */
    public String generateTokenFromUserDetails(ShopUserDetails userPrinciple){
        List<String> roles = userPrinciple.getAuthorities()
                .stream().map(GrantedAuthority::getAuthority).toList();

        Date now = Date.from(clock.instant());
        return Jwts.builder()
                .subject(userPrinciple.getEmail())
                .claim("id",userPrinciple.getId())
                .claim("roles",roles)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + authTokenProperties.getExpirationInMils()))
                .signWith(key())
                .compact();
    }

    private SecretKey key(){
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(authTokenProperties.getJwtSecret()));
    }

    // Expiry is checked against the injected clock, the same one tokens are minted with.
    private JwtParser parser() {
        return Jwts.parser().verifyWith(key()).clock(() -> Date.from(clock.instant())).build();
    }

    String getUserNameFromToken(String token){
        return parser().parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean validateToken(String token) {
        try {
            parser().parseSignedClaims(token);
            return true;
        }catch (Exception e){
            throw new JwtException(e.getMessage());
        }
    }

}
