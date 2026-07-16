package com.aryan.spring_security_demo.security.jwt;

import com.aryan.spring_security_demo.security.user.UserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.List;

import static io.jsonwebtoken.Jwts.*;
@Component
public class JwtUtils {
    private String jwtSecret;
    private int expirationTime;


    public String generateUserTokenFromUser(Authentication authentication){
        UserDetails userPrinciple = (UserDetails)  authentication.getPrincipal();
        List<String> roles = userPrinciple.getAuthorities()
                .stream().map(GrantedAuthority::getAuthority).toList();

        return builder()
                .setSubject(userPrinciple.getEmail())
                .claim("id",userPrinciple.getId())
                .claim("roles",roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() +expirationTime))
                .signWith(key(),SignatureAlgorithm.HS256).compact();
    }
private Key key(){
      return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
}

private String getUserNameFromToken(String token){
      return Jwts.parser().setSigningKey(key()).build().parseClaimsJws(token).getBody().getSubject();
}

public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(key()).build().parseClaimsJws(token);
            return true;
        }catch (Exception e){
            throw  new JwtException(e.getMessage());
        }
}

}
