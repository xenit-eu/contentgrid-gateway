package com.contentgrid.gateway.security.jwt.issuer;

import com.contentgrid.gateway.security.jwt.issuer.JwtInternalIssuerConfiguration.ContentgridGatewayJwtProperties;
import com.contentgrid.gateway.security.jwt.issuer.jwk.source.FilebasedJWKSetSource;
import com.contentgrid.gateway.security.jwt.issuer.jwk.source.LoggingJWKSetSourceEventListener;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.support.ResourcePatternResolver;

@RequiredArgsConstructor
class PropertiesBasedJwtSignerRegistry implements JwtSignerRegistry {

    private final Map<String, JwtClaimsSigner> instantiatedSigners = new ConcurrentHashMap<>();
    private final ContentgridGatewayJwtProperties gatewayJwtProperties;
    private final ResourcePatternResolver resourcePatternResolver;


    @Override
    public boolean hasSigner(String signerName) {
        return getJwkSourceMap().containsKey(signerName);
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

    private Map<String, JWKSource<SecurityContext>> getJwkSourceMap() {
        Map<String, JWKSource<SecurityContext>> jwkSourceMap = new HashMap<>();
        gatewayJwtProperties.getSigners().keySet().stream().forEach(
                signerName -> {
                    var signerProperties = gatewayJwtProperties.getSigners().get(signerName);
                    if (signerProperties == null) {
                        throw new IllegalArgumentException(
                                "No JWT signer named '%s'. Available signers are %s".formatted(signerName,
                                        gatewayJwtProperties.getSigners().keySet()));
                    }
                    var jwkSource = new FilebasedJWKSetSource(
                            resourcePatternResolver,
                            signerProperties.getActiveKeys(),
                            signerProperties.getRetiredKeys()
                    );
                    jwkSourceMap.put(signerName, JWKSourceBuilder.create(jwkSource)
                            .refreshAheadCache(JWKSourceBuilder.DEFAULT_REFRESH_AHEAD_TIME, true, new LoggingJWKSetSourceEventListener<>())
                            .build());
                }
        );

        return jwkSourceMap;
    }

    private JwtClaimsSigner createSigner(String signerName) {
        if (!hasSigner(signerName)) {
            throw new IllegalArgumentException(
                    "No JWT signer named '%s'. Available signers are %s".formatted(signerName,
                            getJwkSourceMap().keySet()));
        }

        return new PropertiesBasedJwtClaimsSigner(getJwkSourceMap().get(signerName), gatewayJwtProperties.getSigners().get(signerName).getAlgorithms());
    }

}
