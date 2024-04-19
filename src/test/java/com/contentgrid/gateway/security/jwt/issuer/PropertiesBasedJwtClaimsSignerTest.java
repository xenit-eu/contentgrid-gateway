package com.contentgrid.gateway.security.jwt.issuer;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.security.jwt.issuer.PropertiesBasedJwtClaimsSigner.JwtClaimsSignerProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSAlgorithm.Family;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSSignerFactory;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.AsymmetricJWK;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Singular;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Delegate;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.security.util.InMemoryResource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

class PropertiesBasedJwtClaimsSignerTest {

    @SneakyThrows
    static KeyPair createKeyPair(String algorithm, int size) {
        var generator = KeyPairGenerator.getInstance(algorithm);
        generator.initialize(size);
        return generator.generateKeyPair();
    }

    @SneakyThrows
    static Resource toKeyResource(List<PemObject> objects) {

        var privateKeyOutput = new ByteArrayOutputStream();
        try(var writer = new OutputStreamWriter(privateKeyOutput)) {
            try(var pemWriter = new PemWriter(writer)) {
                for (PemObject object : objects) {
                    pemWriter.writeObject(object);
                }
            }
        }
        return new InMemoryResource(privateKeyOutput.toByteArray());

    }

    static Resource toPrivateKeyResource(KeyPair keyPair) {
        return toKeyResource(List.of(
                new PemObject("PRIVATE KEY", keyPair.getPrivate().getEncoded()),
                new PemObject("PUBLIC KEY", keyPair.getPublic().getEncoded()))
        );
    }

    static Resource toPublicKeyResource(KeyPair keyPair) {
        return toKeyResource(List.of(
                new PemObject("PUBLIC KEY", keyPair.getPublic().getEncoded())
        ));
    }

    static Random createDeterministicRandom() {
        // This is a random that will always start from the same seed
        return new Random(5);
    }

    @Test
    void signs_with_active_rsa_key() throws ParseException, JOSEException {
        var activeKey = createKeyPair("RSA", 2048);
        var resolver = MockResourcePatternResolver.builder()
                .resource("file:/keys/active.pem", toPrivateKeyResource(activeKey))
                .build();

        var signer = new PropertiesBasedJwtClaimsSigner(MockJwtClaimsSignerProperties.builder()
                .activeKeys("file:/keys/active.pem")
                .build(),
                resolver
        );

        var signedJwt = signer.sign(JWTClaimsSet.parse(Map.of("test", "test")));

        var rsaVerifier = new RSASSAVerifier((RSAPublicKey) activeKey.getPublic());

        assertThat(signedJwt.verify(rsaVerifier)).isTrue();
    }

    @Test
    void provides_all_signing_keys() throws ParseException, JOSEException {
        var activeKey = createKeyPair("RSA", 2048);
        var retiredKey1 = createKeyPair("RSA", 2048);
        var retiredKey2 = createKeyPair("RSA", 2048);
        var resolver = MockResourcePatternResolver.builder()
                .resource("file:/keys/active.pem", toPrivateKeyResource(activeKey))
                .resource("file:/keys/retired-1.pem", toPrivateKeyResource(retiredKey1))
                .resource("file:/keys/retired-2.pem", toPublicKeyResource(retiredKey2))
                .build();

        var signer = new PropertiesBasedJwtClaimsSigner(
                MockJwtClaimsSignerProperties.builder()
                        .activeKeys("file:/keys/active.pem")
                        .allKeys("file:/keys/retired-*.pem")
                        .build(),
                resolver
        );

        var signedJwt = signer.sign(JWTClaimsSet.parse(Map.of("test", "test")));

        var rsaVerifier = new RSASSAVerifier((RSAPublicKey) activeKey.getPublic());

        assertThat(signedJwt.verify(rsaVerifier)).isTrue();

        assertThat(signer.getSigningKeys()).satisfies(jwks -> {
            assertThat(jwks.getKeys()).hasSize(3);
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(toPublicKeyResource(activeKey).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(toPublicKeyResource(retiredKey1).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(toPublicKeyResource(retiredKey2).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
        });
    }

    @Test
    void uses_new_keys_when_rotated() throws ParseException, JOSEException {
        var newActiveKey = createKeyPair("RSA", 4096);
        var oldActiveKey = createKeyPair("RSA", 2048);
        var oldRetiredKey = createKeyPair("RSA", 2048);
        var oldResolver = MockResourcePatternResolver.builder()
                .resource("file:/keys/active.pem", toPrivateKeyResource(oldActiveKey))
                .resource("file:/keys/retired-1.pem", toPublicKeyResource(oldRetiredKey))
                .build();

        var newResolver = MockResourcePatternResolver.builder()
                .resource("file:/keys/active.pem", toPrivateKeyResource(newActiveKey))
                .resource("file:/keys/retired-1.pem", toPrivateKeyResource(oldActiveKey))
                .resource("file:/keys/retired-2.pem", toPublicKeyResource(oldRetiredKey))
                .build();

        var resolver = new DelegateResourcePatternResolver(oldResolver);

        var signer = new PropertiesBasedJwtClaimsSigner(
                MockJwtClaimsSignerProperties.builder()
                        .activeKeys("file:/keys/active.pem")
                        .allKeys("file:/keys/retired-*.pem")
                        .build(),
                resolver
        );

        var oldSignedJwt = signer.sign(JWTClaimsSet.parse(Map.of("test", "test")));

        var oldRsaVerifier = new RSASSAVerifier((RSAPublicKey) oldActiveKey.getPublic());

        assertThat(oldSignedJwt.verify(oldRsaVerifier)).isTrue();

        assertThat(signer.getSigningKeys()).satisfies(jwks -> {
            assertThat(jwks.getKeys()).hasSize(2);
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(toPublicKeyResource(oldActiveKey).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(toPublicKeyResource(oldRetiredKey).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
        });

        // Rotate the keys
        resolver.setDelegate(newResolver);

        var newSignedJwt = signer.sign(JWTClaimsSet.parse(Map.of("test", "test")));

        var newRsaVerifier = new RSASSAVerifier((RSAPublicKey) newActiveKey.getPublic());

        assertThat(newSignedJwt.verify(newRsaVerifier)).isTrue();

        assertThat(signer.getSigningKeys()).satisfies(jwks -> {
            assertThat(jwks.getKeys()).hasSize(3);
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(toPublicKeyResource(newActiveKey).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(toPublicKeyResource(oldActiveKey).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(toPublicKeyResource(oldRetiredKey).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
        });

    }

    @Test
    void rotates_multiple_active_keys() throws ParseException, JOSEException {
        var activeKey1 = createKeyPair("RSA", 4096);
        var activeKey2 = createKeyPair("RSA", 2048);

        var resolver = MockResourcePatternResolver.builder()
                .resource("file:/keys/active-1.pem", toPrivateKeyResource(activeKey1))
                .resource("file:/keys/active-2.pem", toPrivateKeyResource(activeKey2))
                .build();

        var signer = new PropertiesBasedJwtClaimsSigner(
                MockJwtClaimsSignerProperties.builder()
                        .activeKeys("file:/keys/active-*.pem")
                        .build(),
                resolver,
                new DefaultJWSSignerFactory(),
                createDeterministicRandom()
        );

        Set<String> signingKeyIds = new HashSet<>();
        // try 20 times to sign JWTs, so we can collect a sample of the signing keys
        // Signing keys are selected "randomly" using the deterministic random above,
        // so it is not left up to chance which keys are actually selected.
        for (int i = 0; i < 20; i++) {
            var signedJwt = signer.sign(JWTClaimsSet.parse(Map.of("test", "test")));
            var keyId = signedJwt.getHeader().getKeyID();
            var signingKey = signer.getSigningKeys().getKeyByKeyId(keyId);
            var verifier = new DefaultJWSVerifierFactory().createJWSVerifier(signedJwt.getHeader(),
                    ((AsymmetricJWK) signingKey).toPublicKey());

            assertThat(signedJwt.verify(verifier)).isTrue();
            signingKeyIds.add(keyId);
        }
        assertThat(signingKeyIds).hasSize(2); // Both o
    }

    @Test
    void uses_allowed_signing_methods_only() throws ParseException, JOSEException {
        var activeKey1 = createKeyPair("RSA", 4096);
        var activeKey2 = createKeyPair("EC", 256);

        var resolver = MockResourcePatternResolver.builder()
                .resource("file:/keys/active-1.pem", toPrivateKeyResource(activeKey1))
                .resource("file:/keys/active-2.pem", toPrivateKeyResource(activeKey2))
                .build();

        var signer = new PropertiesBasedJwtClaimsSigner(
                MockJwtClaimsSignerProperties.builder()
                        .activeKeys("file:/keys/active-*.pem")
                        .algorithms(Set.of(JWSAlgorithm.ES256))
                        .build(),
                resolver,
                new DefaultJWSSignerFactory(),
                createDeterministicRandom()
        );

        // try 20 times to sign JWTs, so we can collect a sample of the signing keys
        // Signing keys are selected "randomly" using the deterministic random above,
        // so it is not left up to chance which keys are actually selected.
        // The keys selected are actually the same order as in the rotates_multiple_active_keys test,
        // so if selecting only allowed algorithms was not working, we would expect signatures with both keysn
        // given that the rotates_multiple_active_keys test is passing
        for(int i = 0; i < 20; i++) {
            var signedJwt = signer.sign(JWTClaimsSet.parse(Map.of("test", "test")));
            assertThat(signedJwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
            var verifier = new ECDSAVerifier((ECPublicKey) activeKey2.getPublic());

            assertThat(signedJwt.verify(verifier)).isTrue();
        }

    }

    @RequiredArgsConstructor
    private static class DelegateResourcePatternResolver implements ResourcePatternResolver {
        @Delegate
        @Setter
        @NonNull
        private ResourcePatternResolver delegate;
    }

    @RequiredArgsConstructor
    @Builder
    private static class MockResourcePatternResolver implements ResourcePatternResolver {
        @Singular
        private final Map<String, Resource> resources;

        private final PathMatcher pathMatcher = new AntPathMatcher();


        @Override
        public Resource getResource(String location) {
            return resources.getOrDefault(location, new NonExistingResource(location));
        }

        @Override
        public ClassLoader getClassLoader() {
            return null;
        }

        @Override
        public Resource[] getResources(String locationPattern) throws IOException {
            return resources.keySet()
                    .stream()
                    .filter(path -> pathMatcher.match(locationPattern, path))
                    .map(this::getResource)
                    .toArray(Resource[]::new);
        }

        @RequiredArgsConstructor
        private static class NonExistingResource extends AbstractResource {
            private final String path;

            @Override
            public String getDescription() {
                return "NonExistingResource [%s]".formatted(path);
            }

            @Override
            public InputStream getInputStream() throws IOException {
                throw new FileNotFoundException(getDescription()+" can not be opened because it does not exist");
            }

            @Override
            public boolean exists() {
                return false;
            }
        }
    }

    @Value
    @Builder
    static class MockJwtClaimsSignerProperties implements JwtClaimsSignerProperties {
        String activeKeys;
        String allKeys;
        @Default
        Set<JWSAlgorithm> algorithms = Family.SIGNATURE;
    }
}