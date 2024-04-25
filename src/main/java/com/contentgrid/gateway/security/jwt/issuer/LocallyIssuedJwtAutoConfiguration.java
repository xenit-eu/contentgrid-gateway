package com.contentgrid.gateway.security.jwt.issuer;

import com.contentgrid.gateway.security.jwt.issuer.LocallyIssuedJwtAutoConfiguration.ContentgridGatewayJwtProperties;
import com.contentgrid.gateway.security.jwt.issuer.actuate.JWKSetEndpoint;
import com.nimbusds.jose.JWSAlgorithm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.conditional.ConditionalOnEnabledFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

@RequiredArgsConstructor
@AutoConfiguration
@AutoConfigureAfter({ReactiveOAuth2ResourceServerAutoConfiguration.class, ReactiveOAuth2ClientAutoConfiguration.class})
@EnableConfigurationProperties(ContentgridGatewayJwtProperties.class)
@Slf4j
public class LocallyIssuedJwtAutoConfiguration {
    
    @Bean
    JwtSignerRegistry jwtSignerRegistry(ContentgridGatewayJwtProperties properties, ApplicationContext applicationContext) {
        return new PropertiesBasedJwtSignerRegistry(properties, applicationContext);
    }

    @Bean
    @Primary
    AuthenticationInformationResolver authenticationInformationResolver(
            List<AuthenticationInformationResolver> resolvers) {
        return new CompositeAuthenticationInformationResolver(resolvers);
    }

    @Bean
    @Order(0)
    JwtAuthenticationTokenAuthenticationInformationResolver jwtAuthenticationTokenAuthenticationInformationResolver() {
        return new JwtAuthenticationTokenAuthenticationInformationResolver();
    }

    @Bean
    @ConditionalOnBean({ReactiveOAuth2AuthorizedClientManager.class, ReactiveOAuth2UserService.class})
    @Order(10)
    AuthenticationInformationResolver plainOAuth2UserDetailsAuthenticationInformationResolver(
            ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
            @Autowired(required = false) ReactiveOAuth2UserService<OAuth2UserRequest, OAuth2User> plainOAuth2UserService
    ) {
        if(plainOAuth2UserService != null) {
            return new PlainOAuth2UserDetailsAuthenticationInformationResolver(authorizedClientManager, plainOAuth2UserService);
        }
        return null;
    }

    @Bean
    @ConditionalOnBean({ReactiveOAuth2AuthorizedClientManager.class, ReactiveOAuth2UserService.class})
    @Order(10)
    AuthenticationInformationResolver oidcUserDetailsAuthenticationInformationResolver(
            ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
            @Autowired(required = false) ReactiveOAuth2UserService<OidcUserRequest, OidcUser> oidcUserService
    ) {
        if(oidcUserService != null) {
            return new OIDCUserDetailsAuthenticationInformationResolver(authorizedClientManager, oidcUserService);
        }
        return null;
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    JWKSetEndpoint jwkSetEndpoint(JwtSignerRegistry jwtSignerRegistry) {
        return new JWKSetEndpoint(jwtSignerRegistry::getJWKSet);
    }

    @Bean
    @ConditionalOnEnabledFilter
    LocallyIssuedJwtGatewayFilterFactory locallyIssuedJwtGatewayFilterFactory(
            ApplicationContext applicationContext,
            JwtSignerRegistry jwtSignerRegistry,
            AuthenticationInformationResolver authenticationInformationResolver
    ) {
        return new LocallyIssuedJwtGatewayFilterFactory(applicationContext, jwtSignerRegistry::getRequiredSigner, authenticationInformationResolver);
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
