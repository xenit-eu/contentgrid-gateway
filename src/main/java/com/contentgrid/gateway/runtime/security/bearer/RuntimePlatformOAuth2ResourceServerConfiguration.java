package com.contentgrid.gateway.runtime.security.bearer;

import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.routing.ApplicationIdRequestResolver;
import com.contentgrid.gateway.runtime.security.authority.ClaimUtil;
import com.contentgrid.gateway.runtime.security.authority.ExtensionDelegationGrantedAuthorityConverter;
import com.contentgrid.gateway.runtime.security.bearer.RuntimePlatformExternalIssuerProperties.OidcIssuerProperties;
import com.contentgrid.gateway.runtime.security.jwt.ContentGridAudiences;
import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.authority.ActorConverter;
import com.contentgrid.gateway.security.authority.ActorConverterType;
import com.contentgrid.gateway.security.authority.UserGrantedAuthorityConverter;
import com.contentgrid.gateway.security.bearer.DynamicJwtAuthenticationManagerResolver;
import com.contentgrid.gateway.security.bearer.IssuerGatedJwtAuthenticationManager;
import com.contentgrid.gateway.security.bearer.PostValidatingJwtAuthenticationManager;
import com.contentgrid.gateway.security.bearer.ReactiveJwtDecoderBuilder;
import com.contentgrid.gateway.security.oidc.ReactiveClientRegistrationIdResolver;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.DelegatingReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2ResourceServerSpec;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint.DelegateEntry;
import org.springframework.security.web.server.authentication.AuthenticationConverterServerWebExchangeMatcher;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled")
@EnableConfigurationProperties(RuntimePlatformExternalIssuerProperties.class)
public class RuntimePlatformOAuth2ResourceServerConfiguration {

    @Bean
    @ActorConverterType(ActorType.USER)
    ActorConverter runtimeUserActorConverter(
            RuntimePlatformExternalIssuerProperties externalIssuerProperties
    ) {
        var nonUserIssuers = new HashSet<String>();
        nonUserIssuers.add(externalIssuerProperties.getExtensionSystem().getIssuer());
        nonUserIssuers.add(externalIssuerProperties.getExtensionDelegation().getIssuer());

        return new ActorConverter(Predicate.not(nonUserIssuers::contains), ActorType.USER, ClaimUtil::userClaims);
    }

    @Bean
    @ActorConverterType(ActorType.EXTENSION)
    ActorConverter runtimeExtensionActorConverter(
            RuntimePlatformExternalIssuerProperties externalIssuerProperties
    ) {
        var converter = new ActorConverter(
                Predicate.isEqual(externalIssuerProperties.getExtensionSystem().getIssuer()),
                ActorType.EXTENSION,
                ClaimUtil::extensionSystemClaims
        );

        // Extensions can have nested actors
        converter.setParentActorConverter(converter);

        return converter;
    }

    @Bean
    Customizer<OAuth2ResourceServerSpec> configureRuntimeJwtAuthenticationManagerResolver(
            ApplicationIdRequestResolver applicationIdResolver,
            ReactiveClientRegistrationIdResolver registrationIdResolver,
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            RuntimePlatformExternalIssuerProperties externalIssuerProperties,
            @ActorConverterType(ActorType.USER)
            Converter<ClaimAccessor, Actor> userActorConverter,
            @ActorConverterType(ActorType.EXTENSION)
            Converter<ClaimAccessor, Actor> extensionActorConverter,
            @Autowired(required = false) ExtensionDelegationGrantedAuthorityConverter extensionDelegationGrantedAuthorityConverter
    ) {
        return spec -> {
            // The general idea here is: there are 3 different issuers that are trusted for different ways of authenticating
            // 1. A normal, application-specific, issuer; used directly for user authentication (this one is resolved by the DynamicJwtAuthenticationManagerResolver)
            // 2. A shared issuer for extension system authentication: used for extension authentication with a service account (extensionSystemAuthenticationManager)
            // 3. A shared issuer for delegated authentication by an extension: used for authenticating an extension on-behalf-of the user (extensionDelegationAuthenticationManager)
            //
            // For these shared issuers, we only want to create one shared AuthenticationManager that can be shared across all applications.
            // However, for shared issuers, we also MUST verify the 'aud' claim, so it is only acceptable for the application that the token was
            // issued for. If the audience is not checked, users for different applications (or organizations) would be able to log in to the application.
            //
            // Because signing keys are cached by the ReactiveJwtDecoder, we don't want to create separate decoders for every application.
            // Normally, audience checks are part of ReactiveJwtDecoder, but here we are left with checking the 'aud' claim with a wrapping ReactiveAuthenticationManager
            var resolver = new DynamicJwtAuthenticationManagerResolver(
                    applicationIdResolver,
                    registrationIdResolver,
                    clientRegistrationRepository
            );

            var extensionSystemAuthenticationManager = createAuthenticationManager(
                    externalIssuerProperties.getExtensionSystem(),
                    new UserGrantedAuthorityConverter(extensionActorConverter)
            );
            var extensionDelegationAuthenticationManager = createAuthenticationManager(
                    externalIssuerProperties.getExtensionDelegation(),
                    extensionDelegationGrantedAuthorityConverter
            );
            resolver.setAuthenticationManagerConfigurer(authenticationManager -> {
                var authenticationConverter = new ReactiveJwtAuthenticationConverter();
                authenticationConverter.setJwtGrantedAuthoritiesConverter(new ReactiveJwtGrantedAuthoritiesConverterAdapter(new UserGrantedAuthorityConverter(
                        userActorConverter)));
                authenticationManager.setJwtAuthenticationConverter(authenticationConverter);
            });
            resolver.setPostProcessor((authenticationManager, webExchange) ->
                    applicationIdResolver.resolveApplicationId(webExchange)
                            .map(applicationId -> Mono.<ReactiveAuthenticationManager>just(
                                    new DelegatingReactiveAuthenticationManager(
                                            createApplicationSpecificExtensionAuthenticationManager(
                                                    applicationId,
                                                    extensionSystemAuthenticationManager,
                                                    extensionDelegationAuthenticationManager
                                            ),
                                            authenticationManager
                                    ))
                            )
                            .orElse(Mono.just(authenticationManager))
            );
            spec.authenticationManagerResolver(resolver);
        };
    }

    private static PostValidatingJwtAuthenticationManager<List<String>> createApplicationSpecificExtensionAuthenticationManager(
            ApplicationId applicationId,
            ReactiveAuthenticationManager extensionSystemAuthenticationManager,
            ReactiveAuthenticationManager extensionDelegationAuthenticationManager
    ) {
        return new PostValidatingJwtAuthenticationManager<>(
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(ContentGridAudiences.application(applicationId))),
                new DelegatingReactiveAuthenticationManager(
                        extensionSystemAuthenticationManager,
                        extensionDelegationAuthenticationManager
                )
        );
    }

    @Bean
    Customizer<List<DelegateEntry>> runtimeBearerTokenEntryPoint() {
        return entries -> {
            var converter = new ServerBearerTokenAuthenticationConverter();
            var matcher = new AuthenticationConverterServerWebExchangeMatcher(converter);

            entries.add(0, new DelegateEntry(matcher, new BearerTokenServerAuthenticationEntryPoint()));
        };
    }

    private static ReactiveAuthenticationManager createAuthenticationManager(
            OidcIssuerProperties issuerProperties,
            Converter<Jwt, Collection<GrantedAuthority>> converter
    ) {
        if (converter == null || issuerProperties.getIssuer() == null) {
            return auth -> Mono.empty(); // If no converter available, or no issuer is set, create an authentication manager that skips
        }
        var jwtDecoder = ReactiveJwtDecoderBuilder.create()
                .issuer(issuerProperties.getIssuer())
                .jwkSetUri(issuerProperties.getJwkSetUri())
                .jwsAlgorithms(issuerProperties.getJwsAlgorithms())
                .build();
        var jwtAuthenticationManager = new JwtReactiveAuthenticationManager(jwtDecoder);
        var jwtAuthenticationConverter = new ReactiveJwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new ReactiveJwtGrantedAuthoritiesConverterAdapter(converter));
        jwtAuthenticationManager.setJwtAuthenticationConverter(jwtAuthenticationConverter);

        return new IssuerGatedJwtAuthenticationManager(
                issuerProperties.getIssuer()::equals,
                jwtAuthenticationManager
        );
    }
}
