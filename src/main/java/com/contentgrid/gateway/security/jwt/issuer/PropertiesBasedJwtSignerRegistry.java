package com.contentgrid.gateway.security.jwt.issuer;

import com.contentgrid.gateway.security.jwt.issuer.LocallyIssuedJwtAutoConfiguration.ContentgridGatewayJwtProperties;
import com.nimbusds.jose.jwk.JWKSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.support.ResourcePatternResolver;

@RequiredArgsConstructor
class PropertiesBasedJwtSignerRegistry implements JwtSignerRegistry {

    private final ContentgridGatewayJwtProperties properties;
    private final ResourcePatternResolver resourcePatternResolver;
    private final Map<String, JwtClaimsSigner> instantiatedSigners = new ConcurrentHashMap<>();

    @Override
    public boolean hasSigner(String signerName) {
        return properties.getSigners().containsKey(signerName);
    }

    @Override
    public Optional<JWKSet> getJWKSet(String signerName) {
        if (hasSigner(signerName)) {
            return Optional.ofNullable(getRequiredSigner(signerName).getSigningKeys().toPublicJWKSet());
        }
        return Optional.empty();
    }

    @Override
    public Optional<JwtClaimsSigner> getSigner(String signerName) {
        if (!hasSigner(signerName)) {
            return Optional.empty();
        } else {
            return Optional.of(getRequiredSigner(signerName));
        }
    }

    @Override
    public JwtClaimsSigner getRequiredSigner(String signerName) {
        return instantiatedSigners.computeIfAbsent(signerName, this::createSigner);
    }

    private JwtClaimsSigner createSigner(String signerName) {
        var signerProperties = properties.getSigners().get(signerName);
        if (signerProperties == null) {
            throw new IllegalArgumentException(
                    "No JWT signer named '%s'. Available signers are %s".formatted(signerName,
                            properties.getSigners().keySet()));
        }
        return new PropertiesBasedJwtClaimsSigner(signerProperties, resourcePatternResolver);
    }

}
