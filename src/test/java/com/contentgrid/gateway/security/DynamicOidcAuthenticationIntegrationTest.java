package com.contentgrid.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.configuration.api.fragments.ConfigurationFragment;
import com.contentgrid.configuration.api.fragments.DynamicallyConfigurable;
import com.contentgrid.configuration.applications.ApplicationConfiguration;
import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.routing.ApplicationIdRequestResolver;
import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.test.security.TestAuthenticationDetails;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.web.reactive.server.WebTestClient.RequestHeadersSpec;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@Slf4j
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "contentgrid.gateway.runtime-platform.enabled=true"
        }
)
class DynamicOidcAuthenticationIntegrationTest extends AbstractKeycloakIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    DynamicallyConfigurable<String, ApplicationId, ApplicationConfiguration> applicationConfigurationRepository;

    @TestConfiguration(proxyBeanMethods = false)
    static class IntegrationTestConfiguration {

        @Bean
        @Primary
        ApplicationIdRequestResolver applicationIdRequestResolver() {
            return exchange -> {
                var header = exchange.getRequest().getHeaders().getFirst("Test-ApplicationId");
                return Optional.ofNullable(header).map(ApplicationId::from);
            };
        }
    }

    @Test
    void confidentialClient_oidcLogin() {
        // keycloak setup
        var realm = createRealm("test-confidential-client");
        var client = createConfidentialClient(realm, "confidential", "http://localhost:" + port + "/*");
        var user = createUser(realm, "test");

        // create gateway app configuration
        var appId = ApplicationId.random();
        applicationConfigurationRepository.register(new ConfigurationFragment<>(
                "test",
                appId,
                ApplicationConfiguration.builder()
                        .clientId(client.clientId())
                        .clientSecret(client.clientSecret())
                        .issuerUri(realm.getIssuerUrl())
                        .build()
        ));

        log.info("Starting confidential OIDC authz code flow");

        // Initialize OIDC flow with the gateway
        var authzCodeRequest = this.getConfidentialAuthorizationCodeRequest(client, appId);
        // Get the Authorization Code from Keycloak
        var authzCodeResponse = this.getAuthorizationCodeResponse(authzCodeRequest.uri(), user);
        // Complete the OAuth2 Login with the gateway
        var sessionCookie = this.completeOAuth2Login(authzCodeRequest.getSessionCookie(), authzCodeResponse, appId);

        this.assertRequest_withSessionCookie(appId, sessionCookie.getValue())
                .expectStatus().is2xxSuccessful()
                .expectBody(TestAuthenticationDetails.class)
                .value(authenticationDetails -> {
                    assertThat(authenticationDetails.getPrincipal()).satisfies(principal -> {
                        assertThat(principal.getType()).isEqualTo(ActorType.USER);
                        assertThat(principal.getClaims().getClaimAsString(JwtClaimNames.ISS)).isEqualTo(realm.getIssuerUrl());
                        assertThat(principal.getClaims().getClaimAsString(JwtClaimNames.SUB)).isEqualTo(user.userId());
                    });
                    assertThat(authenticationDetails.getActor()).isNull();
                });

        // request with old session cookie should fail and get redirected back to login
        this.assertRequest_withSessionCookie(appId, authzCodeRequest.getSessionCookie())
                .expectStatus()
                .is3xxRedirection()
                .expectHeader().value("location", loc -> assertThat(loc).startsWith("/oauth2/authorization/"));

        // requesting data with a given session cookie to a different app-id,
        // should result in the session cookie being invalidated and the user directed to login again
        this.assertRequest_withSessionCookie(ApplicationId.random(), sessionCookie.getValue())
                .expectStatus()
                .is3xxRedirection()
                .expectHeader().value("Set-Cookie", value -> assertThat(value).startsWith("SESSION="));

        applicationConfigurationRepository.revoke("test");
    }

    @Test
    void publicClient_authorizationCodeFlow_withPKCE() throws GeneralException, IOException {

        var realm = createRealm("test-public-client");
        var client = createPublicClient(realm, "public-client", "http://localhost:9999");
        var user = createUser(realm, "test");

        var appId = ApplicationId.random();
        applicationConfigurationRepository.register(new ConfigurationFragment<>(
                "config-id",
                appId,
                ApplicationConfiguration.builder()
                        .clientId(client.clientId())
                        .issuerUri(realm.getIssuerUrl())
                        .build()
        ));

        log.info("Starting public OIDC authz code flow");

        // fetch OIDC metadata
        var metadata = OIDCProviderMetadata.resolve(Issuer.parse(realm.getIssuerUrl()));
        assertThat(metadata).isNotNull();

        // create the authorization request
        var authzCodeRequest = this.createPkceAuthorizationCodeRequest(metadata.getAuthorizationEndpointURI(), client);
        // get authorization code, with keycloak login
        var authzCodeResponse = this.getAuthorizationCodeResponse(authzCodeRequest.uri(), user);

        // exchange the Authorization Code (+ PKCE code verifier) for an Access Token with the Keycloak token endpoint
        var tokenResponse = this.completeTokenExchange(client, metadata.getTokenEndpointURI(), authzCodeResponse,
                authzCodeRequest.getCodeVerifier());
        assertThat(tokenResponse.isSuccess()).isTrue();
        assertThat(tokenResponse.getAccessToken()).isNotNull();

        // validate what we can do with the access token
        assertRequest_withBearer(appId, tokenResponse.getAccessToken())
                .expectStatus().is2xxSuccessful()
                .expectBody(TestAuthenticationDetails.class)
                .value(authenticationDetails -> {
                    assertThat(authenticationDetails.getPrincipal()).satisfies(principal -> {
                        assertThat(principal.getType()).isEqualTo(ActorType.USER);
                        assertThat(principal.getClaims().getClaimAsString(JwtClaimNames.ISS)).isEqualTo(realm.getIssuerUrl());
                        assertThat(principal.getClaims().getClaimAsString(JwtClaimNames.SUB)).isEqualTo(user.userId());
                    });
                    assertThat(authenticationDetails.getActor()).isNull();
                });

        // HTTP 401 - because bearer token is invalid
        assertRequest_withBearer(appId, "").expectStatus().isUnauthorized();
        // HTTP 401 - no redirect expected, because Accept header is not compatible with HTML
        assertRequest_withBearer(appId, null).expectStatus().isUnauthorized();
        // HTTP 401 - request not associated with an app-id
        assertRequest_withBearer(null, tokenResponse.getAccessToken()).expectStatus().isUnauthorized();
        // HTTP 401 - access token cannot be associated with app-id
        assertRequest_withBearer(ApplicationId.from("invalid"), tokenResponse.getAccessToken()).expectStatus().isUnauthorized();

        // revoke app-id configuration
        applicationConfigurationRepository.revoke("config-id");
        // HTTP 401 - no client-registration associated with app-id anymore
        assertRequest_withBearer(appId, tokenResponse.getAccessToken()).expectStatus().isUnauthorized();

    }

    private ResponseSpec assertRequest_withBearer(ApplicationId appId, String accessToken) {
        return this.assertRequest(request -> {
            // json-only request simulating fetch/XHR
            request.accept(MediaType.APPLICATION_JSON);

            if (appId != null) {
                request.header("Test-ApplicationId", appId.getValue());
            }

            if (accessToken != null) {
                request.header("Authorization", "Bearer " + accessToken);
            }
        });
    }

    private ResponseSpec assertRequest_withSessionCookie(ApplicationId appId, String session) {
        return this.assertRequest(request -> {
            request.accept(MediaType.TEXT_HTML, MediaType.ALL);

            if (appId != null) {
                request.header("Test-ApplicationId", appId.getValue());
            }

            if (session != null) {
                request.cookie("SESSION", session);
            }
        });
    }

    private ResponseSpec assertRequest(Consumer<RequestHeadersSpec<? extends RequestHeadersSpec<?>>> customizer) {
        var request = this.http.get()
                .uri("http://localhost:%s/_test/authenticationDetails".formatted(this.port));

        customizer.accept(request);

        return request.exchange();
    }


}
