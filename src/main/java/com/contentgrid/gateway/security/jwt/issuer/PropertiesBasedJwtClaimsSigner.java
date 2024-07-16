package com.contentgrid.gateway.security.jwt.issuer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.factories.DefaultJWSSignerFactory;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.CachingJWKSetSource;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.jwk.source.URLBasedJWKSetSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.proc.SimpleSecurityContext;
import com.nimbusds.jose.produce.JWSSignerFactory;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

@RequiredArgsConstructor
public class PropertiesBasedJwtClaimsSigner implements JwtClaimsSigner {

    private final JWSSignerFactory jwsSignerFactory;
    private final Random random;
    private final JWKSource<SecurityContext> jwkSource;
    private final Set<JWSAlgorithm> algorithms;


    public PropertiesBasedJwtClaimsSigner(JWKSource<SecurityContext> jwkSource, Set<JWSAlgorithm> algorithms) {
        this(new DefaultJWSSignerFactory(), new Random(), jwkSource, algorithms);
    }

    public interface JwtClaimsSignerProperties {
        String getActiveKeys();
        String getRetiredKeys();
        Set<JWSAlgorithm> getAlgorithms();
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
    public SignedJWT sign(JWTClaimsSet jwtClaimsSet) throws JOSEException {
        var jwks = new ArrayList<>(getAllSigningKeys());

        Collections.shuffle(jwks, this.random); // Randomly shuffle our active keys, so we pick an arbitrary one first

        Set<JWSAlgorithm> algorithmsSupportedByKeys = new HashSet<>();

        for (JWK selectedKey : jwks) {
            if (selectedKey.getExpirationTime() != null && !new Date().before(selectedKey.getExpirationTime())) {
                // Skip retired keys
                continue;
            }

            var selectedSigner = jwsSignerFactory.createJWSSigner(selectedKey);
            algorithmsSupportedByKeys.addAll(selectedSigner.supportedJWSAlgorithms());
            var firstSupportedAlgorithm = algorithms
                    .stream()
                    .filter(selectedSigner.supportedJWSAlgorithms()::contains)
                    .findFirst();
            if(firstSupportedAlgorithm.isEmpty()) {
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
        throw new IllegalStateException("No active signing keys support any of the configured algorithms (%s); algorithms that can be used by these keys are %s".formatted(
                algorithms,
                algorithmsSupportedByKeys
        ));
    }
}
