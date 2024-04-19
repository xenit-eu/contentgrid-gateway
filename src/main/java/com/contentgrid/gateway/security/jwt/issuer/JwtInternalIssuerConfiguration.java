package com.contentgrid.gateway.security.jwt.issuer;

import com.contentgrid.gateway.security.jwt.issuer.JwtInternalIssuerConfiguration.ContentgridGatewayJwtProperties;
import com.contentgrid.gateway.security.jwt.issuer.actuate.JWKSetEndpoint;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.conditional.ConditionalOnEnabledFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;

@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ContentgridGatewayJwtProperties.class)
@Slf4j
public class JwtInternalIssuerConfiguration {
    
    @Bean
    JwtSignerRegistry jwtSignerRegistry(ContentgridGatewayJwtProperties properties, ApplicationContext applicationContext) {
        return new PropertiesBasedJwtSignerRegistry(properties, applicationContext);
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    JWKSetEndpoint jwkSetEndpoint(JwtSignerRegistry jwtSignerRegistry) {
        return new JWKSetEndpoint(jwtSignerRegistry::getJWKSet);
    }

    @Bean
    @ConditionalOnEnabledFilter
    LocallyIssuedJwtGatewayFilterFactory internalJwtIssuerTokenRelayGatewayFilterFactory(ApplicationContext applicationContext, JwtSignerRegistry jwtSignerRegistry) {
        return new LocallyIssuedJwtGatewayFilterFactory(applicationContext, jwtSignerRegistry::getRequiredSigner);
    }

    @RequiredArgsConstructor
    static class PropertiesBasedJwtSignerRegistry implements JwtSignerRegistry {
        private final ContentgridGatewayJwtProperties properties;
        private final ResourcePatternResolver resourcePatternResolver;
        private final Map<String, JwtClaimsSigner> instantiatedSigners = new ConcurrentHashMap<>();

        @Override
        public boolean hasSigner(String signerName) {
            return properties.getSigners().containsKey(signerName);
        }

        @Override
        public Optional<JWKSet> getJWKSet(String signerName) {
            if(hasSigner(signerName)) {
                return Optional.ofNullable(getRequiredSigner(signerName).getSigningKeys().toPublicJWKSet());
            }
            return Optional.empty();
        }

        @Override
        public Optional<JwtClaimsSigner> getSigner(String signerName) {
            if(!hasSigner(signerName)) {
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
            if(signerProperties == null) {
                throw new IllegalArgumentException("No JWT signer named '%s'. Available signers are %s".formatted(signerName, properties.getSigners().keySet()));
            }
            return new PropertiesBasedJwtClaimsSigner(signerProperties, resourcePatternResolver);
        }

    }

    @ConfigurationProperties("contentgrid.gateway.jwt")
    @Valid
    @Data
    @Getter
    static class ContentgridGatewayJwtProperties {
        @Valid
        private Map<String, JwtSignerProperties> signers = new HashMap<>();
    }


    @Data
    static class JwtSignerProperties implements PropertiesBasedJwtClaimsSigner.JwtClaimsSignerProperties {
        @NotNull
        private String activeKeys;
        private String allKeys;
        private Set<JWSAlgorithm> algorithms = Set.of(JWSAlgorithm.RS256);
    }
}
