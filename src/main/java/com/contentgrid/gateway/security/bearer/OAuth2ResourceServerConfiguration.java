package com.contentgrid.gateway.security.bearer;

import com.contentgrid.gateway.runtime.RuntimeRequestResolver;
import com.contentgrid.gateway.security.oidc.ReactiveClientRegistrationIdResolver;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2ResourceServerSpec;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint.DelegateEntry;
import org.springframework.security.web.server.authentication.AuthenticationConverterServerWebExchangeMatcher;

@Configuration(proxyBeanMethods = false)
public class OAuth2ResourceServerConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled")
    static class RuntimePlatformOAuth2ResourceServerConfiguration {

        @Bean
        Customizer<OAuth2ResourceServerSpec> runtimeJwtAuthenticationManagerResolver(
                RuntimeRequestResolver runtimeRequestResolver,
                ReactiveClientRegistrationIdResolver registrationIdResolver,
                ReactiveClientRegistrationRepository clientRegistrationRepository) {
            return spec -> {
                var resolver = new DynamicJwtAuthenticationManagerResolver(
                        runtimeRequestResolver,
                        registrationIdResolver,
                        clientRegistrationRepository);
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

    @Bean
    Customizer<OAuth2ResourceServerSpec> defaultJwtBearerAuth(ObjectProvider<ReactiveJwtDecoder> jwtDecoders) {
        var decoder = jwtDecoders.getIfAvailable();
        if (decoder == null) {
            return null;
        }

        return spec -> spec.jwt(jwt -> jwt.jwtDecoder(decoder));
    }
}
