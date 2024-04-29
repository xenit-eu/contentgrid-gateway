package com.contentgrid.gateway.security.oidc;

import com.contentgrid.gateway.security.refresh.AuthenticationRefresher;
import java.time.Clock;
import java.time.Duration;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.authentication.ReactiveOidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoderFactory;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class OidcIdTokenAuthenticationRefresher implements AuthenticationRefresher {
    @NonNull
    private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;

    @NonNull
    private final ReactiveOAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> accessTokenResponseClient;

    @NonNull
    @Setter
    private ReactiveJwtDecoderFactory<ClientRegistration> jwtDecoderFactory = new ReactiveOidcIdTokenDecoderFactory();

    @NonNull
    private final ReactiveOAuth2UserService<OidcUserRequest, OidcUser> userService;

    @NonNull
    @Setter
    private GrantedAuthoritiesMapper authoritiesMapper = authorities -> authorities;

    @NonNull
    @Setter
    private Duration clockSkew = Duration.ofMinutes(1);

    @NonNull
    @Setter
    private Clock clock = Clock.systemUTC();

    @Override
    public Mono<Authentication> refresh(Authentication authentication, ServerWebExchange exchange) {
        if(authentication instanceof OAuth2AuthenticationToken oAuth2AuthenticationToken &&  oAuth2AuthenticationToken.getPrincipal() instanceof OidcUser oidcUser) {
            if(shouldTokenBeRefreshed(oidcUser.getIdToken())) {
                return doRefresh(oAuth2AuthenticationToken, exchange).checkpoint("OidcIdTokenAuthenticationRefresher");
            } else {
                return Mono.just(authentication);
            }
        }
        return Mono.empty();
    }

    private boolean shouldTokenBeRefreshed(OidcIdToken idToken) {
        var idTokenExpires = idToken.getExpiresAt();
        if(idTokenExpires == null) {
            return false; // Token never expires, so doesn't have to be refreshed
        }

        // token expires within clockSkew seconds: refresh token now
        return clock.instant().isAfter(idTokenExpires.minus(clockSkew));
    }

    private Mono<Authentication> doRefresh(OAuth2AuthenticationToken oAuth2AuthenticationToken,
            ServerWebExchange exchange) {
        // Load authorized client
        return authorizedClientRepository.loadAuthorizedClient(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId(), oAuth2AuthenticationToken, exchange)
                .flatMap(authorizedClient -> {
                    // perform a token refresh
                     return accessTokenResponseClient.getTokenResponse(new OAuth2RefreshTokenGrantRequest(authorizedClient.getClientRegistration(), authorizedClient.getAccessToken(), authorizedClient.getRefreshToken()))
                             // Remove authorized client when token refresh fails
                             .onErrorResume(OAuth2AuthorizationException.class, ex -> authorizedClientRepository.removeAuthorizedClient(authorizedClient.getClientRegistration().getRegistrationId(), oAuth2AuthenticationToken, exchange)
                                     .then(Mono.error(ex))
                             )
                             .onErrorMap(OAuth2AuthorizationException.class, ex -> new OAuth2AuthenticationException(ex.getError(), ex))
                             // store the refreshed client credentials
                             .flatMap(accessTokenResponse -> authorizedClientRepository.saveAuthorizedClient(updateAuthorizedClient(authorizedClient, accessTokenResponse), oAuth2AuthenticationToken, exchange)
                                     .thenReturn(accessTokenResponse)
                             )
                             // Create the new authentication from the token response
                             .flatMap(accessTokenResponse -> createAuthenticationFromTokenResponse(authorizedClient.getClientRegistration(), accessTokenResponse));
                });
    }

    private static OAuth2AuthorizedClient updateAuthorizedClient(OAuth2AuthorizedClient oldClient, OAuth2AccessTokenResponse oAuth2AccessTokenResponse) {
        return new OAuth2AuthorizedClient(
                oldClient.getClientRegistration(),
                oldClient.getPrincipalName(),
                oAuth2AccessTokenResponse.getAccessToken(),
                oAuth2AccessTokenResponse.getRefreshToken()
        );
    }

    // See also OidcAuthorizationCodeReactiveAuthenticationManager
    private Mono<Authentication> createAuthenticationFromTokenResponse(ClientRegistration clientRegistration, OAuth2AccessTokenResponse accessTokenResponse) {
        var accessToken = accessTokenResponse.getAccessToken();
        return createOidcIdToken(clientRegistration, accessTokenResponse)
                .map(idToken -> new OidcUserRequest(clientRegistration, accessToken, idToken, accessTokenResponse.getAdditionalParameters()))
                .flatMap(this.userService::loadUser)
                .map(oauth2User -> {
                    var mappedAuthorities = authoritiesMapper.mapAuthorities(oauth2User.getAuthorities());
                    return new OAuth2AuthenticationToken(oauth2User, mappedAuthorities, clientRegistration.getRegistrationId());
                });
    }

    private Mono<OidcIdToken> createOidcIdToken(ClientRegistration clientRegistration, OAuth2AccessTokenResponse accessTokenResponse) {
        var jwtDecoder = this.jwtDecoderFactory.createDecoder(clientRegistration);
        var rawIdToken = (String) accessTokenResponse.getAdditionalParameters().get(OidcParameterNames.ID_TOKEN);
        if(rawIdToken == null) {
            var invalidIdTokenError = new OAuth2Error("invalid_id_token", "Missing required ID Token in Token Response for Client Registration: "+clientRegistration.getRegistrationId(), null);
            return Mono.error(new OAuth2AuthenticationException(invalidIdTokenError, invalidIdTokenError.toString()));
        }
        return jwtDecoder.decode(rawIdToken)
                .map((jwt) -> new OidcIdToken(jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getClaims()));

    }
}
