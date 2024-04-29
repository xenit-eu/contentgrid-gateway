package com.contentgrid.gateway.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoderFactory;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class OidcIdTokenAuthenticationRefresherTest {

    public static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);


    private static final String CLIENT_REGISTRATION_ID = "test-client-registration";
    private static final ClientRegistration CLIENT_REGISTRATION = ClientRegistration.withRegistrationId(CLIENT_REGISTRATION_ID)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();

    @Test
    void keepsNonExpiredToken() {
        var expiry = CLOCK.instant().plus(2, ChronoUnit.MINUTES);
        var authorizedClient = new OAuth2AuthorizedClient(CLIENT_REGISTRATION, "test",
                new OAuth2AccessToken(TokenType.BEARER, "access-token", CLOCK.instant().minus(1, ChronoUnit.SECONDS), expiry),
                new OAuth2RefreshToken("refresh-token", CLOCK.instant().minus(1, ChronoUnit.SECONDS), expiry.plus(1, ChronoUnit.HOURS))
        );
        var authorizedClientRepo = new InMemoryServerOAuth2AuthorizedClientRepository(authorizedClient);
        var refreshClient = Mockito.mock(ReactiveOAuth2AccessTokenResponseClient.class);
        var userService = new NoopOidcReactiveOAuth2UserService();

        var authenticationRefresher = new OidcIdTokenAuthenticationRefresher(
                authorizedClientRepo,
                refreshClient,
                userService
        );

        authenticationRefresher.setJwtDecoderFactory(new PlainJwtReactiveJwtDecoderFactory());
        authenticationRefresher.setClock(CLOCK);


        var authentication = new OAuth2AuthenticationToken(
                new DefaultOidcUser(List.of(), OidcIdToken.withTokenValue("XXX")
                        .subject("test")
                        .expiresAt(expiry)
                        .build()),
                List.of(),
                CLIENT_REGISTRATION_ID
        );

        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());

        assertThat(authenticationRefresher.refresh(authentication, exchange).block()).isInstanceOfSatisfying(OAuth2AuthenticationToken.class, newAuthentication -> {
            assertThat(newAuthentication).isSameAs(authentication);
            assertThat(newAuthentication.getPrincipal()).isInstanceOfSatisfying(OidcUser.class, oidcUser -> {
                assertThat(oidcUser.getExpiresAt()).isCloseTo(expiry, within(1, ChronoUnit.SECONDS));
            });
        });

        // check that existing access and refresh token are still being used
        assertThat(authorizedClientRepo.loadAuthorizedClient(CLIENT_REGISTRATION_ID, authentication, exchange).block()).satisfies(authorizedClient1 -> {
            assertThat(authorizedClient1.getAccessToken().getTokenValue()).isEqualTo("access-token");
            assertThat(authorizedClient1.getRefreshToken().getTokenValue()).isEqualTo("refresh-token");
        });
    }

    @Test
    void refreshesExpiredToken() {
        var expiry = CLOCK.instant().minus(1, ChronoUnit.MINUTES);
        var authorizedClient = new OAuth2AuthorizedClient(CLIENT_REGISTRATION, "test",
                new OAuth2AccessToken(TokenType.BEARER, "access-token", expiry.minus(1, ChronoUnit.SECONDS), expiry),
                new OAuth2RefreshToken("refresh-token", expiry.minus(1, ChronoUnit.SECONDS), CLOCK.instant().plus(1, ChronoUnit.HOURS))
        );
        var authorizedClientRepo = new InMemoryServerOAuth2AuthorizedClientRepository(authorizedClient);
        var refreshClient = Mockito.mock(ReactiveOAuth2AccessTokenResponseClient.class);
        var userService = new NoopOidcReactiveOAuth2UserService();

        var authenticationRefresher = new OidcIdTokenAuthenticationRefresher(
                authorizedClientRepo,
                refreshClient,
                userService
        );

        authenticationRefresher.setJwtDecoderFactory(new PlainJwtReactiveJwtDecoderFactory());
        authenticationRefresher.setClock(CLOCK);


        var authentication = new OAuth2AuthenticationToken(
                new DefaultOidcUser(List.of(), OidcIdToken.withTokenValue("XXX")
                        .subject("test")
                        .expiresAt(expiry)
                        .build()),
                List.of(),
                CLIENT_REGISTRATION_ID
        );

        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());


        Mockito.when(refreshClient.getTokenResponse(Mockito.any()))
                .thenReturn(Mono.just(OAuth2AccessTokenResponse.withToken("access-token-2")
                                .refreshToken("refresh-token-2")
                                .expiresIn(360)
                                .tokenType(TokenType.BEARER)
                                .additionalParameters(Map.of(
                                        "id_token", new PlainJWT(new JWTClaimsSet.Builder()
                                                .subject("test")
                                                .expirationTime(Date.from(CLOCK.instant().plus(5, ChronoUnit.MINUTES)))
                                                .build()).serialize()
                                ))
                        .build()
                ));

        assertThat(authenticationRefresher.refresh(authentication, exchange).block()).isInstanceOfSatisfying(OAuth2AuthenticationToken.class, newAuthentication -> {
            assertThat(newAuthentication.getPrincipal()).isInstanceOfSatisfying(OidcUser.class, oidcUser -> {
                assertThat(oidcUser.getExpiresAt()).isCloseTo(
                        CLOCK.instant().plus(5, ChronoUnit.MINUTES), within(1, ChronoUnit.SECONDS));
            });
        });

        // check that new access and refresh token is stored
        assertThat(authorizedClientRepo.loadAuthorizedClient(CLIENT_REGISTRATION_ID, authentication, exchange).block()).satisfies(authorizedClient1 -> {
            assertThat(authorizedClient1.getAccessToken().getTokenValue()).isEqualTo("access-token-2");
            assertThat(authorizedClient1.getRefreshToken().getTokenValue()).isEqualTo("refresh-token-2");
        });
    }

    @Test
    void handlesTokenRefreshFailure() {

        var expiry = CLOCK.instant().minus(1, ChronoUnit.MINUTES);
        var authorizedClient = new OAuth2AuthorizedClient(CLIENT_REGISTRATION, "test",
                new OAuth2AccessToken(TokenType.BEARER, "access-token", expiry.minus(1, ChronoUnit.SECONDS), expiry),
                new OAuth2RefreshToken("refresh-token", expiry.minus(1, ChronoUnit.SECONDS), CLOCK.instant().plus(1, ChronoUnit.HOURS))
        );
        var authorizedClientRepo = new InMemoryServerOAuth2AuthorizedClientRepository(authorizedClient);
        var refreshClient = Mockito.mock(ReactiveOAuth2AccessTokenResponseClient.class);
        var userService = new NoopOidcReactiveOAuth2UserService();

        var authenticationRefresher = new OidcIdTokenAuthenticationRefresher(
                authorizedClientRepo,
                refreshClient,
                userService
        );

        authenticationRefresher.setJwtDecoderFactory(new PlainJwtReactiveJwtDecoderFactory());
        authenticationRefresher.setClock(CLOCK);


        var authentication = new OAuth2AuthenticationToken(
                new DefaultOidcUser(List.of(), OidcIdToken.withTokenValue("XXX")
                        .subject("test")
                        .expiresAt(expiry)
                        .build()),
                List.of(),
                CLIENT_REGISTRATION_ID
        );

        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());


        Mockito.when(refreshClient.getTokenResponse(Mockito.any()))
                .thenReturn(Mono.error(new OAuth2AuthorizationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT))));

        assertThatThrownBy(() -> authenticationRefresher.refresh(authentication, exchange).blockOptional())
                .isInstanceOf(OAuth2AuthenticationException.class);

        // check that authorized client is removed
        assertThat(authorizedClientRepo.loadAuthorizedClient(CLIENT_REGISTRATION_ID, authentication, exchange).blockOptional()).isEmpty();
    }

    private static class NoopOidcReactiveOAuth2UserService implements
            ReactiveOAuth2UserService<OidcUserRequest, OidcUser> {

        @Override
        public Mono<OidcUser> loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
            return Mono.just(new DefaultOidcUser(null, userRequest.getIdToken()));
        }
    }

    private static class PlainJwtReactiveJwtDecoderFactory implements ReactiveJwtDecoderFactory<ClientRegistration> {

        @Override
        public ReactiveJwtDecoder createDecoder(ClientRegistration context) {
            return token -> {
                try {
                    var nimbusJwt = JWTParser.parse(token);
                    var jwt = Jwt.withTokenValue(token)
                            .headers(headers -> headers.putAll(nimbusJwt.getHeader().toJSONObject()))
                            .claims(claims -> {
                                try {
                                    claims.putAll(
                                            MappedJwtClaimSetConverter.withDefaults(Map.of())
                                                    .convert(nimbusJwt.getJWTClaimsSet().getClaims())
                                    );
                                } catch (ParseException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .build();
                    return Mono.just(jwt);
                } catch (ParseException e) {
                    throw new BadJwtException(e.getMessage(), e);
                }
            };
        }
    }

    private static class InMemoryServerOAuth2AuthorizedClientRepository implements ServerOAuth2AuthorizedClientRepository{
        private Map<String, OAuth2AuthorizedClient> clients = new HashMap<>();

        public InMemoryServerOAuth2AuthorizedClientRepository(OAuth2AuthorizedClient ...authorizedClients) {
            for (OAuth2AuthorizedClient authorizedClient : authorizedClients) {
                saveAuthorizedClient(authorizedClient, null, null);
            }
        }

        @Override
        public <T extends OAuth2AuthorizedClient> Mono<T> loadAuthorizedClient(String clientRegistrationId,
                Authentication principal, ServerWebExchange exchange) {
            return Mono.justOrEmpty((T)clients.get(clientRegistrationId));
        }

        @Override
        public Mono<Void> saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
                ServerWebExchange exchange) {
            clients.put(authorizedClient.getClientRegistration().getRegistrationId(), authorizedClient);
            return Mono.just(authorizedClient).then();
        }

        @Override
        public Mono<Void> removeAuthorizedClient(String clientRegistrationId, Authentication principal,
                ServerWebExchange exchange) {
            clients.remove(clientRegistrationId);
            return Mono.just(clientRegistrationId).then();
        }
    }
}