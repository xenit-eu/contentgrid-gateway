package com.contentgrid.gateway.security.bearer;

import com.contentgrid.gateway.security.refresh.AuthenticationRefresher;
import com.contentgrid.gateway.security.refresh.NoopAuthenticationRefresher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2ResourceServerSpec;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration(proxyBeanMethods = false)
public class OAuth2ResourceServerConfiguration {
    @Bean
    AuthenticationRefresher jwtAuthenticationRefresher() {
        return new NoopAuthenticationRefresher(JwtAuthenticationToken.class);
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
