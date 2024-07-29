package com.contentgrid.gateway.security.jwt.issuer.jwk.source;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSetCacheRefreshEvaluator;
import com.nimbusds.jose.jwk.source.JWKSetParseException;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.jwk.source.JWKSetUnavailableException;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Loads JWKs from PEM-encoded or JWK-encoded private key files.
 *
 * There is a distinction made between active and retired signing keys
 */
@RequiredArgsConstructor
public class FilebasedJWKSetSource implements JWKSetSource<SecurityContext> {
    private final ResourcePatternResolver resourcePatternResolver;

    private final String activeSigningKeysPattern;
    private final String retiredSigningKeysPattern;

    @Override
    public JWKSet getJWKSet(JWKSetCacheRefreshEvaluator refreshEvaluator, long currentTime, SecurityContext context)
            throws KeySourceException {
        try {
            Map<String, JWK> keys = new LinkedHashMap<>();
            for (Resource signingKeyResource : resourcePatternResolver.getResources(activeSigningKeysPattern)) {
                var signingKey = createFromSigningKey(signingKeyResource, null);
                keys.put(signingKey.getKeyID(), signingKey);
            }
            if (retiredSigningKeysPattern != null) {
                for (Resource signingKeyResource : resourcePatternResolver.getResources(retiredSigningKeysPattern)) {
                    var signingKey = createFromSigningKey(signingKeyResource, new Date(currentTime));
                    // putIfAbsent, so we don't replace an active key with a retired key if it is matched by both patterns
                    keys.putIfAbsent(signingKey.getKeyID(), signingKey);
                }
            }
            return new JWKSet(new ArrayList<>(keys.values()));
        } catch (IOException e) {
            throw new JWKSetUnavailableException("Failed to load JWKs from file", e);
        } catch (JOSEException e) {
            throw new JWKSetParseException("Failed to load JWKs from keys", e);
        }
    }

    private static JWK createFromSigningKey(Resource resource, Date expirationTime) throws IOException, JOSEException {
        String signingKeyString = resource.getContentAsString(StandardCharsets.UTF_8);
        JWK jwk;
        if(signingKeyString.startsWith("{")) {
            try {
                jwk = JWK.parse(signingKeyString);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Can not parse JWK %s: %s".formatted(resource, e.getMessage()), e);
            }
        } else {
            var decodedKey = JWK.parseFromPEMEncodedObjects(signingKeyString);
            if(decodedKey instanceof RSAKey rsaKey) {
                jwk = new RSAKey.Builder(rsaKey)
                        .keyIDFromThumbprint()
                        .keyUse(KeyUse.SIGNATURE)
                        .expirationTime(expirationTime)
                        .build();
            } else if(decodedKey instanceof ECKey ecKey) {
                jwk = new ECKey.Builder(ecKey)
                        .keyIDFromThumbprint()
                        .keyUse(KeyUse.SIGNATURE)
                        .expirationTime(expirationTime)
                        .build();
            } else {
                throw new IllegalArgumentException("Unsupported JWK %s: Unsupported key type %s".formatted(resource, decodedKey.getKeyType()));
            }
        }

        if(jwk.toPublicJWK() == null) {
            throw new IllegalArgumentException("Unsupported JWK %s: no public key available".formatted(resource));
        }

        return jwk;
    }

    @Override
    public void close() throws IOException {
        // Nothing to close
    }
}
