package com.contentgrid.gateway.security.jwt.issuer;

import static com.contentgrid.gateway.test.security.CryptoTestUtils.createKeyPair;
import static com.contentgrid.gateway.test.security.CryptoTestUtils.toPrivateKeyResource;
import static com.contentgrid.gateway.test.security.CryptoTestUtils.toPublicKeyResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.gateway.security.jwt.issuer.PropertiesBasedJwtClaimsSigner.JwtClaimsSignerProperties;
import com.contentgrid.gateway.security.jwt.issuer.jwk.source.FilebasedJWKSetSource;
import com.contentgrid.gateway.test.util.MockResourcePatternResolver;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSAlgorithm.Family;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSSignerFactory;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.AsymmetricJWK;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.source.JWKSetBasedJWKSource;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jwt.JWTClaimsSet;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.Delegate;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.ResourcePatternResolver;

class PropertiesBasedJwtClaimsSignerTest {

    static Random createDeterministicRandom() {
        // This is a random that will always start from the same seed
        return new Random(5);
    }

    static JWKSource getJwkSource(ResourcePatternResolver resolver) {
        var filebasedJWKSetSource = new FilebasedJWKSetSource(resolver, "file:/keys/active*.pem",
                "file:/keys/retired-*.pem");
        return new JWKSetBasedJWKSource(filebasedJWKSetSource);
    }


    @Test
    void signs_with_active_rsa_key() throws ParseException, JOSEException {
        var activeKey = createKeyPair("RSA", 2048);
        var resolver = MockResourcePatternResolver.builder()
                .resource("file:/keys/active.pem", toPrivateKeyResource(activeKey))
                .build();

        var signer = new PropertiesBasedJwtClaimsSigner(
                getJwkSource(resolver),
                Set.of(JWSAlgorithm.RS256)
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
                getJwkSource(resolver),
                Set.of(JWSAlgorithm.RS256)
        );

        var signedJwt = signer.sign(JWTClaimsSet.parse(Map.of("test", "test")));

        var rsaVerifier = new RSASSAVerifier((RSAPublicKey) activeKey.getPublic());

        assertThat(signedJwt.verify(rsaVerifier)).isTrue();

        assertThat(signer.getSigningKeys()).satisfies(jwks -> {
            assertThat(jwks.getKeys()).hasSize(3);
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(
                    toPublicKeyResource(activeKey).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(
                    toPublicKeyResource(retiredKey1).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(
                    toPublicKeyResource(retiredKey2).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
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
                getJwkSource(resolver),
                Set.of(JWSAlgorithm.RS256)
        );

        var oldSignedJwt = signer.sign(JWTClaimsSet.parse(Map.of("test", "test")));

        var oldRsaVerifier = new RSASSAVerifier((RSAPublicKey) oldActiveKey.getPublic());

        assertThat(oldSignedJwt.verify(oldRsaVerifier)).isTrue();

        assertThat(signer.getSigningKeys()).satisfies(jwks -> {
            assertThat(jwks.getKeys()).hasSize(2);
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(
                    toPublicKeyResource(oldActiveKey).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(
                    toPublicKeyResource(oldRetiredKey).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
        });

        // Rotate the keys
        resolver.setDelegate(newResolver);

        var newSignedJwt = signer.sign(JWTClaimsSet.parse(Map.of("test", "test")));

        var newRsaVerifier = new RSASSAVerifier((RSAPublicKey) newActiveKey.getPublic());

        assertThat(newSignedJwt.verify(newRsaVerifier)).isTrue();

        assertThat(signer.getSigningKeys()).satisfies(jwks -> {
            assertThat(jwks.getKeys()).hasSize(3);
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(
                    toPublicKeyResource(newActiveKey).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(
                    toPublicKeyResource(oldActiveKey).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
            assertThat(jwks.containsJWK(JWK.parseFromPEMEncodedObjects(
                    toPublicKeyResource(oldRetiredKey).getContentAsString(StandardCharsets.UTF_8)))).isTrue();
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
                new DefaultJWSSignerFactory(),
                createDeterministicRandom(),
                getJwkSource(resolver),
                Set.of(JWSAlgorithm.RS256)
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
                new DefaultJWSSignerFactory(),
                createDeterministicRandom(),
                getJwkSource(resolver),
                Set.of(JWSAlgorithm.ES256)
        );

        // try 20 times to sign JWTs, so we can collect a sample of the signing keys
        // Signing keys are selected "randomly" using the deterministic random above,
        // so it is not left up to chance which keys are actually selected.
        // The keys selected are actually the same order as in the rotates_multiple_active_keys test,
        // so if selecting only allowed algorithms was not working, we would expect signatures with both keysn
        // given that the rotates_multiple_active_keys test is passing
        for (int i = 0; i < 20; i++) {
            var signedJwt = signer.sign(JWTClaimsSet.parse(Map.of("test", "test")));
            assertThat(signedJwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
            var verifier = new ECDSAVerifier((ECPublicKey) activeKey2.getPublic());

            assertThat(signedJwt.verify(verifier)).isTrue();
        }

    }

    @Test
    void throws_on_algorith_mismatch() {
        var activeKey1 = createKeyPair("RSA", 4096);

        var resolver = MockResourcePatternResolver.builder()
                .resource("file:/keys/active-1.pem", toPrivateKeyResource(activeKey1))
                .build();

        var signer = new PropertiesBasedJwtClaimsSigner(
                new DefaultJWSSignerFactory(),
                createDeterministicRandom(),
                getJwkSource(resolver),
                Set.of(JWSAlgorithm.ES256)
        );

        assertThrows(IllegalStateException.class, () -> signer.sign(JWTClaimsSet.parse(Map.of("test", "test"))));
    }

    @RequiredArgsConstructor
    private static class DelegateResourcePatternResolver implements ResourcePatternResolver {

        @Delegate
        @Setter
        @NonNull
        private ResourcePatternResolver delegate;
    }

    @Value
    @Builder
    static class MockJwtClaimsSignerProperties implements JwtClaimsSignerProperties {

        String activeKeys;
        String retiredKeys;
        @Default
        Set<JWSAlgorithm> algorithms = Family.SIGNATURE;
    }
}