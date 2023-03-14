package com.contentgrid.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import java.io.IOException;
import java.time.Duration;
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
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.StatusAssertions;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.RequestHeadersSpec;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Testcontainers
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class StaticOidcAuthenticationIntegrationTest extends AbstractKeycloakIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private static final String REALM_NAME = "contentgrid-static-oidc";

    private static final String CLIENT_ID = "confidential-client";
    private static final String CLIENT_SECRET = UUID.randomUUID().toString();

    private final WebTestClient httpClient = WebTestClient
            .bindToServer(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
            .responseTimeout(Duration.ofHours(1)) // for interactive debugging
            .build();

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
    public void keycloakOIDC_redirectFlow() {

        var client = createConfidentialClient(REALM, CLIENT_ID, CLIENT_SECRET, "http://localhost:" + port + "/*");

        // Initialize OIDC flow with the gateway
        var authzCodeRequest = this.getConfidentialAuthorizationCodeRequest(client, null);
        // Get the Authorization Code from Keycloak
        var authzCodeResponse = this.getAuthorizationCodeResponse(authzCodeRequest.uri(), USER);
        // Complete the OAuth2 Login with the gateway
        var sessionCookie = this.completeOAuth2Login(authzCodeRequest.getSessionCookie(), authzCodeResponse);

        this.assertRequest_withSessionCookie(sessionCookie.getValue()).is2xxSuccessful();

//        // try to access a protected resource
//        var initialResponse = rest.get().uri("/")
//                .accept(MediaType.TEXT_HTML)
//                .exchange()
//                .expectStatus().is3xxRedirection()
//                .expectHeader().location("/oauth2/authorization/keycloak")
//                .expectBody()
//                .consumeWith(result -> log.info(result.toString()))
//                .isEmpty();
//
//        // follow the initial redirect
//        // result is another redirect to keycloak
//        var initialRedirect = initialResponse.getResponseHeaders().getLocation();
//        var redirectToKeycloakResponse = rest.get()
//                .uri(Objects.requireNonNull(gatewayBaseUri.resolve(initialRedirect)))
//                .exchange()
//                .expectStatus().is3xxRedirection()
//                .expectHeader().value("location", location -> {
//                    assertThat(location).startsWith(keycloakIssuerUrl());
//                    assertThat(URI.create(location))
//                            .hasParameter("response_type", "code")
//                            .hasParameter("client_id", CONFIDENTIAL_CLIENT_ID)
//                            .hasParameter("scope", "openid profile email");
//                })
//                .expectCookie().exists("SESSION")
//                .expectCookie().httpOnly("SESSION", true)
//                .expectCookie().sameSite("SESSION", "Lax")
//                .expectBody().isEmpty();
//
//        // following the redirect to keycloak
//        // response is an html login form
//        var redirectToKeycloakUri = redirectToKeycloakResponse.getResponseHeaders().getLocation();
//        var keycloakLoginFormResponse = rest.get()
//                .uri(Objects.requireNonNull(redirectToKeycloakUri))
//                .exchange()
//                .expectStatus().is2xxSuccessful()
//                .expectHeader().contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
//                .expectBody().returnResult();
//
//        // submit credentials to keycloak to the form action-url
//        // reponse is a redirect back to the gateway, which contains a query-param 'code'
//        var keycloakLoginResponse = rest.post()
//                .uri(extractFormActionFromHtml(keycloakLoginFormResponse))
//                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
//                .cookies(cookies -> keycloakLoginFormResponse.getResponseCookies().forEach((name, values) -> {
//                    values.stream().map(HttpCookie::getValue).forEach(val -> cookies.add(name, val));
//                }))
//                .body(BodyInserters
//                        .fromFormData("username", "alice")
//                        .with("password", "alice")
//                        .with("credentialId", ""))
//                .exchange()
//                .expectStatus().is3xxRedirection()
//                .expectHeader().value("location", loc -> assertThat(URI.create(loc)).hasParameter("code"))
//                .expectBody().isEmpty();
//
//        // follow the redirect back to the gateway (sending the 'code')
//        // response is a redirect with a new (authenticated) SESSION cookie
//        var sessionCookie = redirectToKeycloakResponse.getResponseCookies().getFirst("SESSION");
//        var appCodeResponse = rest.get()
//                .uri(Objects.requireNonNull(keycloakLoginResponse.getResponseHeaders().getLocation()))
//                .cookie("SESSION", sessionCookie.getValue())
//                .exchange()
//                .expectStatus().is3xxRedirection()
//                .expectHeader().location("/")
//                .expectBody().isEmpty();
//
//        // extract the new session cookie from the response
//        var newSessionCookie = appCodeResponse.getResponseCookies().getFirst("SESSION");
//        // session cookie _should_ update after login (avoiding session fixation issues, etc ..)
//        assertThat(sessionCookie.getValue()).isNotEqualTo(newSessionCookie.getValue());
//        sessionCookie = newSessionCookie;
//
//        // now we can make authenticated requests with the SESSION cookie !
//        rest.get().uri("/me")
//                .cookie("SESSION", sessionCookie.getValue())
//                .exchange()
//                .expectStatus().is2xxSuccessful()
//                .expectBody()
//                .consumeWith(result -> log.info(result.toString()))
//                .jsonPath("$.name").isEqualTo("alice");
    }


    @Test
    public void keycloakOIDCwithPKCE_bearerAuth() throws GeneralException, IOException {

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
        assertRequest_withBearer(tokenResponse.getAccessToken()).is2xxSuccessful();
    }

    @NonNull
    private StatusAssertions assertRequest_withBearer(String accessToken) {
        return this.assertRequest(request -> {
            // json-only request simulating fetch/XHR
            request.accept(MediaType.APPLICATION_JSON);

            if (accessToken != null) {
                request.header("Authorization", "Bearer " + accessToken);
            }
        });
    }

    @NonNull
    private StatusAssertions assertRequest_withSessionCookie(String session) {
        return this.assertRequest(request -> {
            request.accept(MediaType.TEXT_HTML, MediaType.ALL);

            if (session != null) {
                request.cookie("SESSION", session);
            }
        });
    }

    @NonNull
    private StatusAssertions assertRequest(Consumer<RequestHeadersSpec<? extends RequestHeadersSpec<?>>> customizer) {
        var request = this.httpClient.get()
                .uri("http://localhost:%s/me".formatted(this.port));

        customizer.accept(request);

        return request.exchange().expectStatus();
    }

}

