package com.contentgrid.gateway.security.oidc;

import com.contentgrid.gateway.security.refresh.AuthenticationRefresher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.ClientsConfiguredCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2LoginSpec;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;


@Slf4j
@Configuration(proxyBeanMethods = false)
class OidcClientConfiguration {

    @Bean
    AuthenticationRefresher oidcIdTokenAuthenticationRefresher(
            @Autowired(required = false) ServerOAuth2AuthorizedClientRepository authorizedClientRepository,
            ObjectProvider<ReactiveOAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest>> accessTokenResponseClient,
            ObjectProvider<ReactiveOAuth2UserService<OidcUserRequest, OidcUser>> userService,
            ObjectProvider<GrantedAuthoritiesMapper> grantedAuthoritiesMapper
    ) {
        if(authorizedClientRepository == null) {
            return null;
        }
        var tokenRefresher = new OidcIdTokenAuthenticationRefresher(
                authorizedClientRepository,
                accessTokenResponseClient.getIfAvailable(WebClientReactiveRefreshTokenTokenResponseClient::new),
                userService.getIfAvailable(OidcReactiveOAuth2UserService::new)
        );
        grantedAuthoritiesMapper.ifAvailable(tokenRefresher::setAuthoritiesMapper);
        return tokenRefresher;
    }

    @Bean
    @Conditional(ClientsConfiguredCondition.class)
    Customizer<OAuth2LoginSpec> staticOAuth2Login() {
        return oAuth2LoginSpec -> {
            // no-op is fine, this will load defaults
        };
    }


}
