package com.contentgrid.gateway.runtime.security.bearer;

import com.contentgrid.gateway.runtime.routing.ApplicationIdRequestResolver;
import com.contentgrid.gateway.runtime.security.authority.ClaimUtil;
import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.authority.ActorConverter;
import com.contentgrid.gateway.security.authority.UserGrantedAuthorityConverter;
import com.contentgrid.gateway.security.bearer.DynamicJwtAuthenticationManagerResolver;
import com.contentgrid.gateway.security.oidc.ReactiveClientRegistrationIdResolver;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2ResourceServerSpec;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint.DelegateEntry;
import org.springframework.security.web.server.authentication.AuthenticationConverterServerWebExchangeMatcher;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled")
public class RuntimePlatformOAuth2ResourceServerConfiguration {

    @Bean
    Converter<ClaimAccessor, Actor> runtimeUserActorConverter() {
        return new ActorConverter(iss -> true, ActorType.USER, ClaimUtil::userClaims);
    }

    @Bean
    Customizer<OAuth2ResourceServerSpec> runtimeJwtAuthenticationManagerResolver(
            ApplicationIdRequestResolver applicationIdResolver,
            ReactiveClientRegistrationIdResolver registrationIdResolver,
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            UserGrantedAuthorityConverter userGrantedAuthorityConverter
    ) {
        return spec -> {
            var resolver = new DynamicJwtAuthenticationManagerResolver(
                    applicationIdResolver,
                    registrationIdResolver,
                    clientRegistrationRepository
            );

            resolver.setAuthenticationManagerConfigurer(authenticationManager -> {
                var authenticationConverter = new ReactiveJwtAuthenticationConverter();
                authenticationConverter.setJwtGrantedAuthoritiesConverter(new ReactiveJwtGrantedAuthoritiesConverterAdapter(userGrantedAuthorityConverter));
                authenticationManager.setJwtAuthenticationConverter(authenticationConverter);
            });
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

}
