package com.aryan.spring_security_demo.identity.security.jwt;

import com.aryan.spring_security_demo.identity.AuthTokenProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.time.Duration;

/**
 * Signs access tokens with the private key and checks them with the public keys
 * ({@link JwtKeys}). The decoder is what ShopConfig's resource-server support uses
 * on every request that carries a bearer token.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    @Bean
    public JwtEncoder jwtEncoder(JwtKeys keys) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.signingKey())));
    }

    /**
     * Accepts RS256 only, so a token signed any other way (HMAC, or "none") is
     * refused before its claims are read, and only under a key in the published
     * set. Then the claims: the type is a JWT, the token hasn't expired (by the
     * injected clock, with no leeway, since this application both signs and checks
     * them) and the issuer is this one.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtKeys keys, AuthTokenProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<>(keys.publicKeys()))
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        JwtTimestampValidator expiry = new JwtTimestampValidator(Duration.ZERO);
        expiry.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtTypeValidator.jwt(), expiry, new JwtIssuerValidator(properties.getIssuer())));
        return decoder;
    }
}
