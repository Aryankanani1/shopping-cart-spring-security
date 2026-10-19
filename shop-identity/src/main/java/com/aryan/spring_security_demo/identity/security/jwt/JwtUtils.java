package com.aryan.spring_security_demo.identity.security.jwt;

import com.aryan.spring_security_demo.identity.AuthTokenProperties;
import com.aryan.spring_security_demo.identity.security.user.ShopUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Makes access tokens: JWTs signed RS256 with the private key ({@link JwtKeys}),
 * naming the key in their header. Checking them is Spring Security's job
 * (JwtConfig's decoder, ShopJwtAuthenticationConverter).
 */
@Component
@RequiredArgsConstructor
public class JwtUtils {

    /** The user's id, which the token must still match (ShopJwtAuthenticationConverter). */
    public static final String ID_CLAIM = "id";
    /** The user's roles when the token was made, for other services; this one reloads them. */
    public static final String ROLES_CLAIM = "roles";

    private final AuthTokenProperties authTokenProperties;
    private final JwtEncoder jwtEncoder;
    private final JwtKeys jwtKeys;
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

        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authTokenProperties.getIssuer())
                .subject(userPrinciple.getEmail())
                .issuedAt(now)
                .expiresAt(now.plusMillis(authTokenProperties.getExpirationInMils()))
                .claim(ID_CLAIM, userPrinciple.getId())
                .claim(ROLES_CLAIM, roles)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").keyId(jwtKeys.keyId()).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
