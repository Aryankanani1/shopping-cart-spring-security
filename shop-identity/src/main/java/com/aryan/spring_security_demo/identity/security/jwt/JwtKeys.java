package com.aryan.spring_security_demo.identity.security.jwt;

import com.aryan.spring_security_demo.identity.AuthTokenProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The keys access tokens are signed and verified with ({@link AuthTokenProperties}):
 * one RSA private key that signs, and the public keys that verify, which are the
 * signing key's public half and any previous keys still being phased out.
 *
 * <p>Each key's id ({@code kid}) is its JWK thumbprint (RFC 7638), a hash of the
 * public key, so it needs no setting of its own and never changes for a key. A
 * token names the key that signed it, and a verifier picks that key from the set.
 */
@Component
public final class JwtKeys {

    private static final Logger log = LoggerFactory.getLogger(JwtKeys.class);

    private final RSAKey signingKey;
    private final JWKSet publicKeys;

    public JwtKeys(AuthTokenProperties properties) {
        this.signingKey = signingKey(properties);
        // By key id, the signing key first, so a previous key listed twice, or the
        // current one listed as previous, is published once.
        Map<String, JWK> keys = new LinkedHashMap<>();
        keys.put(signingKey.getKeyID(), signingKey.toPublicJWK());
        for (String previous : properties.getPreviousPublicKeys()) {
            if (!previous.isBlank()) {
                RSAKey key = withThumbprintId(new RSAKey.Builder(RsaKeys.parsePublicKey(previous)));
                keys.putIfAbsent(key.getKeyID(), key);
            }
        }
        this.publicKeys = new JWKSet(List.copyOf(keys.values()));
    }

    /** The private key that signs new tokens, with its public half. */
    public RSAKey signingKey() {
        return signingKey;
    }

    /** The signing key's id, which every new token names in its header. */
    public String keyId() {
        return signingKey.getKeyID();
    }

    /** The public keys that verify tokens: the signing key's, then the previous ones. Nothing private. */
    public JWKSet publicKeys() {
        return publicKeys;
    }

    private static RSAKey signingKey(AuthTokenProperties properties) {
        String configured = properties.getSigningKey();
        if (configured != null && !configured.isBlank()) {
            RSAPrivateCrtKey privateKey = RsaKeys.parsePrivateKey(configured);
            return withThumbprintId(new RSAKey.Builder(RsaKeys.publicKeyOf(privateKey)).privateKey(privateKey));
        }
        if (!properties.isGenerateKeyIfMissing()) {
            throw new IllegalStateException("JWT_SIGNING_KEY is not set");
        }
        KeyPair pair = RsaKeys.generate();
        log.warn("No JWT_SIGNING_KEY: signing tokens with a key made at startup. They stop working when "
                + "the application restarts. Set JWT_SIGNING_KEY anywhere but on a developer's machine.");
        return withThumbprintId(new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey(pair.getPrivate()));
    }

    private static RSAKey withThumbprintId(RSAKey.Builder key) {
        try {
            return key.keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).keyIDFromThumbprint().build();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not compute the key's thumbprint", e);
        }
    }
}
