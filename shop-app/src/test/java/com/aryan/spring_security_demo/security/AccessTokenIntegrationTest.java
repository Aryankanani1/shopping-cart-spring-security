package com.aryan.spring_security_demo.security;

import com.aryan.spring_security_demo.cart.CartRepository;
import com.aryan.spring_security_demo.identity.RefreshTokenRepository;
import com.aryan.spring_security_demo.identity.Role;
import com.aryan.spring_security_demo.identity.RoleRepository;
import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.UserRepository;
import com.aryan.spring_security_demo.identity.security.jwt.JwtKeys;
import com.aryan.spring_security_demo.identity.security.jwt.RsaKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access tokens end to end: signed RS256 with the application's private key,
 * verifiable by anyone with the published public keys and by no one without them,
 * and refused, with a 401 from JwtEntryPoint, whenever they don't check out.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessTokenIntegrationTest {

    private static final String EMAIL = "ada@example.com";
    private static final String PASSWORD = "secret123";
    private static final String JWKS = "/api/v1/.well-known/jwks.json";
    // Any URL that needs a signed-in caller and answers 200 for one.
    private static final String SECURED = "/api/v1/notifications";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private JwtKeys jwtKeys;

    private User ada;

    @BeforeEach
    void setUp() {
        clearUsers();
        ada = createUser();
    }

    @Test
    @DisplayName("the JWKS endpoint publishes the public key, and nothing private")
    void theJwksEndpoint_publishesThePublicKeyOnly() throws Exception {
        String body = mockMvc.perform(get(JWKS))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, public"))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].kid").value(jwtKeys.keyId()))
                .andReturn().getResponse().getContentAsString();

        JsonNode key = objectMapper.readTree(body).path("keys").path(0);
        for (String privatePart : List.of("d", "p", "q", "dp", "dq", "qi")) {
            assertThat(key.has(privatePart)).as(privatePart).isFalse();
        }
    }

    // What another service would do: fetch the published keys and verify the token
    // with them alone.
    @Test
    @DisplayName("a login's token verifies with nothing but the published keys")
    void aLoginsToken_verifiesWithThePublishedKeysAlone() throws Exception {
        String token = login();
        JWKSet published = JWKSet.parse(mockMvc.perform(get(JWKS)).andReturn().getResponse().getContentAsString());

        Jwt jwt = NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<>(published)).build().decode(token);

        assertThat(jwt.getSubject()).isEqualTo(EMAIL);
        assertThat(jwt.getHeaders()).containsEntry("kid", jwtKeys.keyId());
        assertThat(((Number) jwt.getClaim("id")).longValue()).isEqualTo(ada.getId());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_CUSTOMER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("shopping-cart-api");
        bearer(get(SECURED), token).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a token signed with any other key is a 401, even if it names our key")
    void aTokenSignedWithAnotherKey_is401() throws Exception {
        KeyPair other = RsaKeys.generate();
        RSAKey forger = new RSAKey.Builder((RSAPublicKey) other.getPublic()).privateKey(other.getPrivate())
                .keyID(jwtKeys.keyId()).build();
        String forged = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(forger)))
                .encode(JwtEncoderParameters.from(jwsHeader(), claimsFor(ada.getId(), Instant.now().plusSeconds(600))))
                .getTokenValue();

        expectRefused(bearer(get(SECURED), forged));
    }

    // Signed with HMAC, with the published public key as the secret, in the hope
    // that the verifier follows the token's "alg".
    @Test
    @DisplayName("an HS256 token keyed with the published public key is a 401")
    void anHs256TokenKeyedWithThePublicKey_is401() throws Exception {
        JWKSet published = JWKSet.parse(mockMvc.perform(get(JWKS)).andReturn().getResponse().getContentAsString());
        byte[] publicKey = ((RSAKey) published.getKeys().get(0)).toRSAPublicKey().getEncoded();
        SignedJWT hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(jwtKeys.keyId()).build(),
                new JWTClaimsSet.Builder().subject(EMAIL).issuer("shopping-cart-api").claim("id", ada.getId())
                        .expirationTime(Date.from(Instant.now().plusSeconds(600))).build());
        hmac.sign(new MACSigner(publicKey));

        expectRefused(bearer(get(SECURED), hmac.serialize()));
    }

    @Test
    @DisplayName("an expired token signed with our own key is a 401")
    void anExpiredToken_is401() throws Exception {
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader(),
                claimsFor(ada.getId(), Instant.now().minusSeconds(60)))).getTokenValue();

        expectRefused(bearer(get(SECURED), expired));
    }

    // The account is deleted and its email signs up again: the first account's
    // token, still unexpired, doesn't sign anyone in as the second.
    @Test
    @DisplayName("a token for a deleted account is a 401, even after its email signs up again")
    void aTokenForADeletedAccount_is401_evenOnceItsEmailSignsUpAgain() throws Exception {
        String token = login();
        clearUsers();
        expectRefused(bearer(get(SECURED), token));

        createUser();
        expectRefused(bearer(get(SECURED), token));
    }

    // RFC 6750: a bearer token that doesn't check out is refused wherever it is
    // sent. Without a token, the public catalogue answers as always.
    @Test
    @DisplayName("an invalid token is a 401 on a public URL too; no token is fine there")
    void anInvalidToken_is401OnAPublicUrl_butNoTokenIsFine() throws Exception {
        expectRefused(bearer(get("/api/v1/products"), "not-a-jwt"));
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
    }

    private void expectRefused(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    private ResultActions bearer(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private JwsHeader jwsHeader() {
        return JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").keyId(jwtKeys.keyId()).build();
    }

    private static JwtClaimsSet claimsFor(long userId, Instant expiresAt) {
        return JwtClaimsSet.builder().issuer("shopping-cart-api").subject(EMAIL)
                .issuedAt(expiresAt.minusSeconds(900)).expiresAt(expiresAt)
                .claim("id", userId).claim("roles", List.of("ROLE_CUSTOMER")).build();
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + EMAIL + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("token").asText();
    }

    private void clearUsers() {
        refreshTokenRepository.deleteAll();  // FK on users — clear before the users
        cartRepository.deleteAll();  // FK on users; a cart's lines go with it
        userRepository.deleteAll();
    }

    private User createUser() {
        Role customer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER")));
        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setEmail(EMAIL);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRoles(Set.of(customer));
        return userRepository.save(user);
    }
}
