package com.contentgrid.gateway.runtime.security.bearer;

import com.contentgrid.gateway.runtime.routing.ApplicationIdRequestResolver;
import com.contentgrid.gateway.runtime.security.authority.ClaimUtil;
import com.contentgrid.gateway.runtime.security.bearer.RuntimePlatformExternalIssuerProperties.OidcIssuerProperties;
import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.authority.ActorConverter;
import com.contentgrid.gateway.security.authority.UserGrantedAuthorityConverter;
import com.contentgrid.gateway.security.bearer.DynamicJwtAuthenticationManagerResolver;
import com.contentgrid.gateway.security.bearer.IssuerGatedJwtAuthenticationManager;
import com.contentgrid.gateway.security.bearer.PostValidatingJwtAuthenticationManager;
import com.contentgrid.gateway.security.bearer.ReactiveJwtDecoderBuilder;
import com.contentgrid.gateway.security.oidc.ReactiveClientRegistrationIdResolver;
import java.util.Collection;
import java.util.List;
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
    ActorConverter runtimeUserActorConverter()  {
        return new ActorConverter(iss -> true, ActorType.USER, ClaimUtil::userClaims);
    }

    @Bean
    Customizer<OAuth2ResourceServerSpec> configureRuntimeJwtAuthenticationManagerResolver(
            ApplicationIdRequestResolver applicationIdResolver,
            ReactiveClientRegistrationIdResolver registrationIdResolver,
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            RuntimePlatformExternalIssuerProperties externalIssuerProperties,
            Converter<ClaimAccessor, Actor> userActorConverter
    ) {
        return spec -> {
            // The general idea here is: there are 2 different issuers that are trusted for different ways of authenticating
            // 1. A normal, application-specific, issuer; used directly for user authentication (this one is resolved by the DynamicJwtAuthenticationManagerResolver)
            // 2. A shared issuer for extension system authentication: used for extension authentication with a service account (extensionSystemAuthenticationManager)
            //
            // For the shared issuers, we only want to create one shared AuthenticationManager that can be shared across all applications.
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
                    new UserGrantedAuthorityConverter(
                            new ActorConverter(
                                    iss -> true,
                                    ActorType.EXTENSION,
                                    ClaimUtil::extensionSystemClaims
                            )
                    )
            );
            resolver.setAuthenticationManagerConfigurer(authenticationManager -> {
                var authenticationConverter = new ReactiveJwtAuthenticationConverter();
                authenticationConverter.setJwtGrantedAuthoritiesConverter(new ReactiveJwtGrantedAuthoritiesConverterAdapter(new UserGrantedAuthorityConverter(
                        userActorConverter)));
                authenticationManager.setJwtAuthenticationConverter(authenticationConverter);
            });
            resolver.setPostProcessor((authenticationManager, webExchange) ->
                    applicationIdResolver.resolveApplicationId(webExchange)
                            .<Mono<ReactiveAuthenticationManager>>map(applicationId -> Mono.just(new DelegatingReactiveAuthenticationManager(
                                    new PostValidatingJwtAuthenticationManager<>(
                                            new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains("contentgrid:application:"+applicationId.getValue())),
                                            extensionSystemAuthenticationManager
                                    ),
                                    authenticationManager
                            )))
                            .orElse(Mono.just(authenticationManager))
            );
            spec.authenticationManagerResolver(resolver);
        };

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
