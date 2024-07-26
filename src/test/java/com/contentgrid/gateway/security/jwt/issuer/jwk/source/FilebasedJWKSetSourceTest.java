package com.contentgrid.gateway.security.jwt.issuer.jwk.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.contentgrid.gateway.test.security.CryptoTestUtils;
import com.nimbusds.jose.jwk.JWK;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class FilebasedJWKSetSourceTest {

    @Test
    @SneakyThrows
    void testGetJWKSet() {
        var activeRSAKey = CryptoTestUtils.createKeyPair("RSA", 2048);
        var activeRSAResource = CryptoTestUtils.toPrivateKeyResource(activeRSAKey);
        var retiredRSAKey = CryptoTestUtils.createKeyPair("RSA", 2048);
        var retiredRSAResource = CryptoTestUtils.toPrivateKeyResource(retiredRSAKey);

        var activeECKey = CryptoTestUtils.createKeyPair("EC", 256);
        var activeECResource = CryptoTestUtils.toPrivateKeyResource(activeECKey);
        var retiredECKey = CryptoTestUtils.createKeyPair("EC", 256);
        var retiredECResource = CryptoTestUtils.toPrivateKeyResource(retiredECKey);

        var activeOctetKeyPair = CryptoTestUtils.createOctetKeyPair();
        var activeOctetResource = CryptoTestUtils.toPrivateKeyResource(activeOctetKeyPair);
        var retiredOctetKeyPair = CryptoTestUtils.createOctetKeyPair();
        var retiredOctetResource = CryptoTestUtils.toPrivateKeyResource(retiredOctetKeyPair);

        var resourcePatternResolver = MockResourcePatternResolver.builder()
                .resource("file:/keys/active_rsa.pem", activeRSAResource)
                .resource("file:/keys/retired_rsa.pem", retiredRSAResource)
                .resource("file:/keys/active_ec.pem", activeECResource)
                .resource("file:/keys/retired_ec.pem", retiredECResource)
                .resource("file:/keys/active_octet.pem", activeOctetResource)
                .resource("file:/keys/retired_octet.pem", retiredOctetResource)
                .build();

        var filebasedJWKSetSource = new FilebasedJWKSetSource(resourcePatternResolver, "file:/keys/active_*.pem",
                "file:/keys/retired_*.pem");

        var jwkSet = filebasedJWKSetSource.getJWKSet(null, System.currentTimeMillis(), null);

        var activeRSAJWK = JWK.parseFromPEMEncodedObjects(activeRSAResource.getContentAsString(StandardCharsets.UTF_8));
        var retiredRSAJWK = JWK.parseFromPEMEncodedObjects(retiredRSAResource.getContentAsString(StandardCharsets.UTF_8));
        var activeECJWK = JWK.parseFromPEMEncodedObjects(activeECResource.getContentAsString(StandardCharsets.UTF_8));
        var retiredECJWK = JWK.parseFromPEMEncodedObjects(retiredECResource.getContentAsString(StandardCharsets.UTF_8));
        var activeOctetJWK = JWK.parseFromPEMEncodedObjects(activeOctetResource.getContentAsString(StandardCharsets.UTF_8));
        var retiredOctetJWK = JWK.parseFromPEMEncodedObjects(retiredOctetResource.getContentAsString(StandardCharsets.UTF_8));

        assertEquals(6, jwkSet.getKeys().size());
        assertNotNull(jwkSet.getKeyByKeyId(activeRSAJWK.computeThumbprint().toString()));
        assertNotNull(jwkSet.getKeyByKeyId(retiredRSAJWK.computeThumbprint().toString()));
        assertNotNull(jwkSet.getKeyByKeyId(activeECJWK.computeThumbprint().toString()));
        assertNotNull(jwkSet.getKeyByKeyId(retiredECJWK.computeThumbprint().toString()));
        assertNotNull(jwkSet.getKeyByKeyId(activeOctetJWK.computeThumbprint().toString()));
        assertNotNull(jwkSet.getKeyByKeyId(retiredOctetJWK.computeThumbprint().toString()));
    }

}