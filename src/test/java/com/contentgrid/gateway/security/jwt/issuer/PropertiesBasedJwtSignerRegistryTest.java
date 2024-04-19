package com.contentgrid.gateway.security.jwt.issuer;

import static com.contentgrid.gateway.security.jwt.issuer.CryptoTestUtils.createKeyPair;
import static com.contentgrid.gateway.security.jwt.issuer.CryptoTestUtils.toPrivateKeyResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.gateway.security.jwt.issuer.JwtInternalIssuerConfiguration.ContentgridGatewayJwtProperties;
import com.contentgrid.gateway.security.jwt.issuer.JwtInternalIssuerConfiguration.JwtSignerProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.interfaces.RSAPublicKey;
import org.junit.jupiter.api.Test;

class PropertiesBasedJwtSignerRegistryTest {

    @Test
    void creates_signers_from_configuration() throws JOSEException {
        var activeKey = createKeyPair("RSA", 2048);

        var resolver = MockResourcePatternResolver.builder()
                .resource("file:/keys/active.pem", toPrivateKeyResource(activeKey))
                .build();

        var properties = new ContentgridGatewayJwtProperties();
        properties.getSigners().put("test", JwtSignerProperties.builder()
                .activeKeys("file:/keys/active.pem")
                .build());

        var registry = new PropertiesBasedJwtSignerRegistry(properties, resolver);

        var signer = registry.getRequiredSigner("test");

        var jwk = new RSAKey.Builder((RSAPublicKey) activeKey.getPublic()).build();
        assertThat(signer.getSigningKeys().containsJWK(jwk)).isTrue();

        assertThat(registry.getSigner("test")).hasValue(signer);

        assertThat(registry.getJWKSet("test")).hasValueSatisfying(jwkSet -> {
            assertThat(jwkSet.getKeys()).isEqualTo(signer.getSigningKeys().toPublicJWKSet().getKeys());
        });

        assertThat(registry.getSigner("invalid")).isEmpty();
        assertThatThrownBy(() -> registry.getRequiredSigner("invalid"));

    }


}