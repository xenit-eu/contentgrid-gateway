package com.contentgrid.gateway.security.bearer;

import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.authority.ActorConverter;
import com.contentgrid.gateway.security.authority.UserGrantedAuthorityConverter;
import com.contentgrid.gateway.security.refresh.AuthenticationRefresher;
import com.contentgrid.gateway.security.refresh.NoopAuthenticationRefresher;
import java.util.Collection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2ResourceServerSpec;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class OAuth2ResourceServerConfiguration {
    @Bean
    AuthenticationRefresher jwtAuthenticationRefresher() {
        return new NoopAuthenticationRefresher(JwtAuthenticationToken.class);
    }

    @Bean
    UserGrantedAuthorityConverter userGrantedAuthorityConverter(ObjectProvider<Converter<ClaimAccessor, Actor>> actorConverter) {
        return new UserGrantedAuthorityConverter(actorConverter.getIfAvailable(this::defaultActorConverter));
    }

    @Bean
    Customizer<OAuth2ResourceServerSpec> defaultJwtBearerAuth(
            ObjectProvider<ReactiveJwtDecoder> jwtDecoders,
            UserGrantedAuthorityConverter grantedAuthorityConverter
    ) {
        var decoder = jwtDecoders.getIfAvailable();
        if (decoder == null) {
            return null;
        }

        return spec -> spec.jwt(jwt -> jwt.jwtDecoder(decoder)
                .jwtAuthenticationConverter(createAuthenticationConverter(grantedAuthorityConverter))
        );
    }

    private static Converter<Jwt, ? extends Mono<? extends AbstractAuthenticationToken>> createAuthenticationConverter(Converter<Jwt, Collection<GrantedAuthority>> authorityConverter) {
        var authenticationConverter = new ReactiveJwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(new ReactiveJwtGrantedAuthoritiesConverterAdapter(authorityConverter));
        return authenticationConverter;
    }

    private Converter<ClaimAccessor, Actor> defaultActorConverter() {
        return new ActorConverter(iss -> true, ActorType.USER, ca -> ca);
    }
}
