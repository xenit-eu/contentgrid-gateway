package com.contentgrid.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.test.security.TestAuthenticationDetails;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient.RequestHeadersSpec;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import org.testcontainers.junit.jupiter.Testcontainers;

@Slf4j
@Testcontainers
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StaticOidcAuthenticationIntegrationTest extends AbstractKeycloakIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private static final String REALM_NAME = "contentgrid-static-oidc";

    private static final String CLIENT_ID = "confidential-client";
    private static final String CLIENT_SECRET = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", REALM::getIssuerUrl);
        registry.add("spring.security.oauth2.client.registration.keycloak.client-id", () -> CLIENT_ID);
        registry.add("spring.security.oauth2.client.registration.keycloak.client-secret", () -> CLIENT_SECRET);
        registry.add("spring.security.oauth2.client.registration.keycloak.scope", () -> "openid, profile, email");

        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", REALM::getIssuerUrl);
    }

    private static Realm REALM;
    private static UserCredentials USER;

    @BeforeAll
    static void setupKeycloak() {
        REALM = createRealm(REALM_NAME);
        USER = createUser(REALM, "user");

    }

    @Test
    void keycloakOIDC_redirectFlow() {

        var client = createConfidentialClient(REALM, CLIENT_ID, CLIENT_SECRET, "http://localhost:" + port + "/*");

        // Initialize OIDC flow with the gateway
        var authzCodeRequest = this.getConfidentialAuthorizationCodeRequest(client, null);
        // Get the Authorization Code from Keycloak
        var authzCodeResponse = this.getAuthorizationCodeResponse(authzCodeRequest.uri(), USER);
        // Complete the OAuth2 Login with the gateway
        var sessionCookie = this.completeOAuth2Login(authzCodeRequest.getSessionCookie(), authzCodeResponse, null);

        this.assertRequest_withSessionCookie(sessionCookie.getValue())
                .expectStatus().is2xxSuccessful()
                .expectBody(TestAuthenticationDetails.class)
                .value(authenticationDetails -> {
                    assertThat(authenticationDetails.getPrincipal()).satisfies(principal -> {
                        assertThat(principal.getType()).isEqualTo(ActorType.USER);
                        assertThat(principal.getClaims().getClaimAsString(JwtClaimNames.ISS)).isEqualTo(REALM.getIssuerUrl());
                        assertThat(principal.getClaims().getClaimAsString(JwtClaimNames.SUB)).isEqualTo(USER.userId());
                    });
                    assertThat(authenticationDetails.getActor()).isNull();
                });
    }


    @Test
    void keycloakOIDCwithPKCE_bearerAuth() throws GeneralException, IOException {

        var client = createPublicClient(REALM, "public-client", "http://localhost:9999");

        // fetch OIDC metadata
        var metadata = OIDCProviderMetadata.resolve(REALM.getIssuer());
        assertThat(metadata).isNotNull();

        // create the authorization request
        var authzCodeRequest = this.createPkceAuthorizationCodeRequest(metadata.getAuthorizationEndpointURI(), client);

        // get authorization code, with keycloak login
        var authzCodeResponse = this.getAuthorizationCodeResponse(authzCodeRequest.uri(), USER);

        // exchange the Authorization Code (+ PKCE code verifier) for an Access Token with the Keycloak token endpoint
        var tokenResponse = this.completeTokenExchange(client, metadata.getTokenEndpointURI(), authzCodeResponse,
                authzCodeRequest.getCodeVerifier());
        assertThat(tokenResponse.isSuccess()).isTrue();
        assertThat(tokenResponse.getAccessToken()).isNotNull();

        // validate what we can do with the access token
        assertRequest_withBearer(tokenResponse.getAccessToken())
                .expectStatus().is2xxSuccessful()
                .expectBody(TestAuthenticationDetails.class)
                .value(authenticationDetails -> {
                    assertThat(authenticationDetails.getPrincipal()).satisfies(principal -> {
                        assertThat(principal.getType()).isEqualTo(ActorType.USER);
                        assertThat(principal.getClaims().getClaimAsString(JwtClaimNames.ISS)).isEqualTo(REALM.getIssuerUrl());
                        assertThat(principal.getClaims().getClaimAsString(JwtClaimNames.SUB)).isEqualTo(USER.userId());
                    });
                    assertThat(authenticationDetails.getActor()).isNull();
                });
    }

    @NonNull
    private ResponseSpec assertRequest_withBearer(String accessToken) {
        return this.assertRequest(request -> {
            // json-only request simulating fetch/XHR
            request.accept(MediaType.APPLICATION_JSON);

            if (accessToken != null) {
                request.header("Authorization", "Bearer " + accessToken);
            }
        });
    }

    @NonNull
    private ResponseSpec assertRequest_withSessionCookie(String session) {
        return this.assertRequest(request -> {
            request.accept(MediaType.TEXT_HTML, MediaType.ALL);

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

