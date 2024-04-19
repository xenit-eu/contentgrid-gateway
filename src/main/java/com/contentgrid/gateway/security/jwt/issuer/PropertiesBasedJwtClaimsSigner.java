package com.contentgrid.gateway.security.jwt.issuer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.factories.DefaultJWSSignerFactory;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.produce.JWSSignerFactory;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    private final JwtClaimsSignerProperties properties;
    private final ResourcePatternResolver resourcePatternResolver;
    private final JWSSignerFactory jwsSignerFactory;
    private final Random random;

    public PropertiesBasedJwtClaimsSigner(JwtClaimsSignerProperties properties, ResourcePatternResolver resourcePatternResolver) {
        this(properties, resourcePatternResolver, new DefaultJWSSignerFactory(), new Random());
    }

    public interface JwtClaimsSignerProperties {
        String getActiveKeys();
        String getAllKeys();
        Set<JWSAlgorithm> getAlgorithms();
    }

    @SneakyThrows
    private static JWK createFromSigningKey(Resource resource) {
        var jwk = JWK.parseFromPEMEncodedObjects(resource.getContentAsString(StandardCharsets.UTF_8));
        if(jwk instanceof RSAKey rsaKey) {
            return new RSAKey.Builder(rsaKey)
                    .keyIDFromThumbprint()
                    .keyUse(KeyUse.SIGNATURE)
                    .build();
        } else if(jwk instanceof ECKey ecKey) {
            return new ECKey.Builder(ecKey)
                    .keyIDFromThumbprint()
                    .keyUse(KeyUse.SIGNATURE)
                    .build();
        } else if(jwk instanceof OctetKeyPair octetKeyPair) {
            return new OctetKeyPair.Builder(octetKeyPair)
                    .keyIDFromThumbprint()
                    .keyUse(KeyUse.SIGNATURE)
                    .build();
        } else {
            throw new IllegalArgumentException("Unsupported JWK key type %s; use RSA, EC or OKP".formatted(jwk.getKeyType()));
        }
    }

    @SneakyThrows
    private Stream<JWK> createSigningKeysFromPath(String path) {
        if(path == null) {
            return Stream.empty();
        }
        return Arrays.stream(resourcePatternResolver.getResources(path))
                .map(PropertiesBasedJwtClaimsSigner::createFromSigningKey);
    }

    private List<JWK> getActiveSigningKeys() {
        return createSigningKeysFromPath(properties.getActiveKeys())
                .toList();
    }

    private List<JWK> getAllSigningKeys() {
        return Stream.concat(
                getActiveSigningKeys().stream(),
                createSigningKeysFromPath(properties.getAllKeys())
        ).toList();
    }

    @Override
    public JWKSet getSigningKeys() {
        return new JWKSet(getAllSigningKeys());
    }

    @Override
    public SignedJWT sign(JWTClaimsSet jwtClaimsSet) throws JOSEException {
        var activeKeys = new ArrayList<>(getActiveSigningKeys());
        Collections.shuffle(activeKeys, this.random); // Randomly shuffle our active keys, so we pick an arbitrary one first

        Set<JWSAlgorithm> algorithmsSupportedByKeys = new HashSet<>();

        for (JWK selectedKey : activeKeys) {
            var selectedSigner = jwsSignerFactory.createJWSSigner(selectedKey);
            algorithmsSupportedByKeys.addAll(selectedSigner.supportedJWSAlgorithms());
            var firstSupportedAlgorithm = properties.getAlgorithms()
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
                properties.getAlgorithms(),
                algorithmsSupportedByKeys
        ));
    }
}
