package com.contentgrid.gateway.security.jwt.issuer;

import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.jwt.issuer.RuntimeAuthenticationJwtClaimsResolver;
import com.contentgrid.gateway.security.jwt.issuer.JwtInternalIssuerConfiguration.ContentgridGatewayJwtProperties;
import com.contentgrid.gateway.security.jwt.issuer.actuate.JWKSetEndpoint;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.PropertiesBasedTextEncryptorFactory;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.PropertiesBasedTextEncryptorFactory.TextEncryptorProperties;
import com.nimbusds.jose.JWSAlgorithm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    LocallyIssuedJwtGatewayFilterFactory internalJwtIssuerTokenRelayGatewayFilterFactory(
            JwtClaimsResolverLocator jwtClaimsResolverLocator,
            JwtSignerRegistry jwtSignerRegistry
    ) {
        return new LocallyIssuedJwtGatewayFilterFactory(
                jwtClaimsResolverLocator::getRequiredClaimsResolver,
                jwtSignerRegistry::getRequiredSigner
        );
    }

    @Bean
    JwtClaimsResolverLocator jwtClaimsResolverLocator() {
        return new JwtClaimsResolverLocator();
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
    @Builder
    @AllArgsConstructor
    static class JwtSignerProperties implements PropertiesBasedJwtClaimsSigner.JwtClaimsSignerProperties {
        @NotNull
        private String activeKeys;
        private String allKeys;
        @Builder.Default
        private Set<JWSAlgorithm> algorithms = Set.of(JWSAlgorithm.RS256);

        public JwtSignerProperties() {
            this.algorithms = Set.of(JWSAlgorithm.RS256);
        }

    }
}
