package com.contentgrid.gateway.security.jwt.issuer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.factories.DefaultJWSSignerFactory;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.proc.SimpleSecurityContext;
import com.nimbusds.jose.produce.JWSSignerFactory;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import lombok.SneakyThrows;
import org.springframework.util.ConcurrentLruCache;


public class JwkSourceJwtClaimsSigner implements JwtClaimsSigner {

    private final Random random;
    private final JWKSource<SecurityContext> jwkSource;
    private final Set<JWSAlgorithm> algorithms;


    public JwkSourceJwtClaimsSigner(JWKSource<SecurityContext> jwkSource, Set<JWSAlgorithm> algorithms) {
        this(new DefaultJWSSignerFactory(), new Random(), jwkSource, algorithms);
    }

    private ConcurrentLruCache<JWK, JWSSigner> signerCache;

    public JwkSourceJwtClaimsSigner(JWSSignerFactory jwsSignerFactory, Random random,
            JWKSource<SecurityContext> jwkSource, Set<JWSAlgorithm> algorithms) {
        this.random = random;
        this.jwkSource = jwkSource;
        this.algorithms = algorithms;

        signerCache = new ConcurrentLruCache<>(10,
                key -> {
                    try {
                        return jwsSignerFactory.createJWSSigner(key);
                    } catch (JOSEException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @SneakyThrows
    private List<JWK> getAllSigningKeys() {
        return jwkSource.get(new JWKSelector(new JWKMatcher.Builder()
                        .keyUse(KeyUse.SIGNATURE)
                        .build()),
                new SimpleSecurityContext());
    }

    @Override
    public JWKSet getSigningKeys() {
        return new JWKSet(getAllSigningKeys());
    }

    @Override
    @SneakyThrows
    public SignedJWT sign(JWTClaimsSet jwtClaimsSet) {
        var jwks = new ArrayList<>(getAllSigningKeys());

        Collections.shuffle(jwks, this.random); // Randomly shuffle our keys, so we pick an arbitrary one first

        Set<JWSAlgorithm> algorithmsSupportedByKeys = new HashSet<>();

        for (JWK selectedKey : jwks) {
            if (selectedKey.getExpirationTime() != null && !new Date().before(selectedKey.getExpirationTime())) {
                // Skip retired keys
                continue;
            }

            var selectedSigner = getJwsSigner(selectedKey);
            algorithmsSupportedByKeys.addAll(selectedSigner.supportedJWSAlgorithms());
            var firstSupportedAlgorithm = algorithms
                    .stream()
                    .filter(selectedSigner.supportedJWSAlgorithms()::contains)
                    .findFirst();
            if (firstSupportedAlgorithm.isEmpty()) {
                // Signer does not support any of the signing algorithms; continue to a next key
                continue;
            }
            var signedJwt = new SignedJWT(new JWSHeader.Builder(firstSupportedAlgorithm.get())
                    .type(JOSEObjectType.JWT)
                    .keyID(selectedKey.getKeyID())
                    .build(),
                    jwtClaimsSet
            );
            signedJwt.sign(selectedSigner);
            return signedJwt;
        }
        throw new IllegalStateException(
                "No active signing keys support any of the configured algorithms (%s); algorithms that can be used by these keys are %s".formatted(
                        algorithms,
                        algorithmsSupportedByKeys
                ));
    }

    private JWSSigner getJwsSigner(JWK jwk) {
        return signerCache.get(jwk);
    }
}
