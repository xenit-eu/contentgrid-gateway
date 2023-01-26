package com.contentgrid.gateway.security;

import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Testcontainers
@Tag("integration")
@ActiveProfiles("keycloak")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OidcAuthenticationIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private static final String REALM_NAME = "contentcloud-gateway";
    private static final String CONFIDENTIAL_CLIENT_ID = "contentcloud-gateway";
    private static final String PUBLIC_CLIENT_ID = "contentcloud-public";

    @Container
    private static final GenericContainer<?> keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:13.0.0")
            .withCopyFileToContainer(MountableFile.forClasspathResource(
                            "contentcloud-gateway-realm.json"),
                    "/tmp/keycloak/contentcloud-gateway-realm.json")
            .withExposedPorts(8080)
            .withLogConsumer(new Slf4jLogConsumer(log))
            .withEnv("KEYCLOAK_IMPORT", "/tmp/keycloak/contentcloud-gateway-realm.json")
            .withEnv("KEYCLOAK_LOGLEVEL", "INFO")
            .waitingFor(Wait.forHttp("/").withStartupTimeout(Duration.of(1, ChronoUnit.MINUTES)));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri",
                OidcAuthenticationIntegrationTest::keycloakIssuerUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                OidcAuthenticationIntegrationTest::keycloakIssuerUrl);
    }

    static String keycloakIssuerUrl() {
        return String.format("http://%s:%s/auth/realms/%s",
                keycloak.getHost(), keycloak.getMappedPort(8080), REALM_NAME);
    }

    @Nested
    class OidcClientIntegrationTest {


        @Test
        public void keycloakOIDC_redirectFlow() {
            var gatewayBaseUri = URI.create("http://localhost:" + port);
            var rest = WebTestClient
                    .bindToServer(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
                    .baseUrl(gatewayBaseUri.toString())
                    .build();

            // try to access a protected resource
            var initialResponse = rest.get().uri("/")
                    .accept(MediaType.TEXT_HTML)
                    .exchange()
                    .expectStatus().is3xxRedirection()
                    .expectHeader().location("/oauth2/authorization/keycloak")
                    .expectBody()
                    .consumeWith(result -> log.info(result.toString()))
                    .isEmpty();

            // follow the initial redirect
            // result is another redirect to keycloak
            var initialRedirect = initialResponse.getResponseHeaders().getLocation();
            var redirectToKeycloakResponse = rest.get()
                    .uri(Objects.requireNonNull(gatewayBaseUri.resolve(initialRedirect)))
                    .exchange()
                    .expectStatus().is3xxRedirection()
                    .expectHeader().value("location", location -> {
                        assertThat(location).startsWith(keycloakIssuerUrl());
                        assertThat(URI.create(location))
                                .hasParameter("response_type", "code")
                                .hasParameter("client_id", CONFIDENTIAL_CLIENT_ID)
                                .hasParameter("scope", "openid profile email");
                    })
                    .expectCookie().exists("SESSION")
                    .expectCookie().httpOnly("SESSION", true)
                    .expectCookie().sameSite("SESSION", "Lax")
                    .expectBody().isEmpty();

            // following the redirect to keycloak
            // response is an html login form
            var redirectToKeycloakUri = redirectToKeycloakResponse.getResponseHeaders().getLocation();
            var keycloakLoginFormResponse = rest.get()
                    .uri(Objects.requireNonNull(redirectToKeycloakUri))
                    .exchange()
                    .expectStatus().is2xxSuccessful()
                    .expectHeader().contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .expectBody().returnResult();

            // submit credentials to keycloak to the form action-url
            // reponse is a redirect back to the gateway, which contains a query-param 'code'
            var keycloakLoginResponse = rest.post()
                    .uri(extractFormActionFromHtml(keycloakLoginFormResponse))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .cookies(cookies -> keycloakLoginFormResponse.getResponseCookies().forEach((name, values) -> {
                        values.stream().map(HttpCookie::getValue).forEach(val -> cookies.add(name, val));
                    }))
                    .body(BodyInserters
                            .fromFormData("username", "alice")
                            .with("password", "alice")
                            .with("credentialId", ""))
                    .exchange()
                    .expectStatus().is3xxRedirection()
                    .expectHeader().value("location", loc -> assertThat(URI.create(loc)).hasParameter("code"))
                    .expectBody().isEmpty();

            // follow the redirect back to the gateway (sending the 'code')
            // response is a redirect with a new (authenticated) SESSION cookie
            var sessionCookie = redirectToKeycloakResponse.getResponseCookies().getFirst("SESSION");
            var appCodeResponse = rest.get()
                    .uri(Objects.requireNonNull(keycloakLoginResponse.getResponseHeaders().getLocation()))
                    .cookie("SESSION", sessionCookie.getValue())
                    .exchange()
                    .expectStatus().is3xxRedirection()
                    .expectHeader().location("/")
                    .expectBody().isEmpty();

            // extract the new session cookie from the response
            var newSessionCookie = appCodeResponse.getResponseCookies().getFirst("SESSION");
            // session cookie _should_ update after login (avoiding session fixation issues, etc ..)
            assertThat(sessionCookie.getValue()).isNotEqualTo(newSessionCookie.getValue());
            sessionCookie = newSessionCookie;

            // now we can make authenticated requests with the SESSION cookie !
            rest.get().uri("/me")
                    .cookie("SESSION", sessionCookie.getValue())
                    .exchange()
                    .expectStatus().is2xxSuccessful()
                    .expectBody()
                    .consumeWith(result -> log.info(result.toString()))
                    .jsonPath("$.name").isEqualTo("alice");
        }


        @Test
        public void keycloakOIDCwithPKCE_bearerAuth() throws GeneralException, IOException {

            // client is here a public client, like a single-page-app
            // (public clients can't hold secrets, like an OAuth2 secret-id)
            // The registered callback URI for the client app
            var clientRedirectURI = URI.create("http://localhost:9085");
            var gatewayBaseUri = URI.create("http://localhost:" + port);

            var http = WebTestClient
                    .bindToServer(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
                    .baseUrl(gatewayBaseUri.toString())
                    .build();

            // fetch oidc metadata
            var metadata = OIDCProviderMetadata.resolve(Issuer.parse(keycloakIssuerUrl()));

            // code-verifier is a random string generated by the client
            var codeVerifier = new CodeVerifier();
            // code-challenge is a SHA-256 hash of the code-verifier
            var codeChallenge = CodeChallenge.compute(CodeChallengeMethod.S256, codeVerifier);
            // random string to link the callback to the authorization request (XSS protection)
            var state = new State();

            // the OIDC authorization request: get an authorization 'code'
            var authorizationRequest = UriComponentsBuilder.fromUri(metadata.getAuthorizationEndpointURI())
                    .queryParam("client_id", PUBLIC_CLIENT_ID)
                    .queryParam("redirect_uri", clientRedirectURI)
                    .queryParam("response_type", "code")
                    .queryParam("scope", "openid email roles")
                    .queryParam("state", state.getValue())
                    // PKCE specific parameters
                    .queryParam("code_challenge", codeChallenge.getValue())
                    .queryParam("code_challenge_method", CodeChallengeMethod.S256.getValue())
                    .build().toUri();

            // get an authorization code, response contains an html login-form
            var authzRequestLoginForm = http.get()
                    .uri(authorizationRequest)
                    .exchange()
                    .expectStatus().is2xxSuccessful()
                    .expectHeader().contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .expectBody().returnResult();

            // login with username & password
            // response is a redirect with a 'code' as query parameter
            var keycloakLoginResponse = http.post()
                    .uri(extractFormActionFromHtml(authzRequestLoginForm))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .cookies(cookies -> {
                        authzRequestLoginForm.getResponseCookies().forEach((name, values) -> {
                            values.stream().map(HttpCookie::getValue).forEach(val -> cookies.add(name, val));
                        });
                    })
                    .body(BodyInserters
                            .fromFormData("username", "alice")
                            .with("password", "alice")
                            .with("credentialId", ""))
                    .exchange()
                    .expectStatus().is3xxRedirection()
                    .expectHeader().value("location", location -> {
                        assertThat(URI.create(location))
                                .hasParameter("session_state")
                                .hasParameter("code")
                                .hasParameter("state", state.getValue());
                    })
                    .expectBody().isEmpty();

            // now the browser would follow the redirect back to the client (= SPA)
            // which can extract the Authorization Code from the url parameter 'code'
            var redirectUrl = keycloakLoginResponse.getResponseHeaders().getLocation();
            var queryParams = UriComponentsBuilder.fromUri(redirectUrl).build().getQueryParams().toSingleValueMap();

            // the client can swap the Authorization Code for an Access Token with the token endpoint
            var tokenResponse = http.post()
                    .uri(metadata.getTokenEndpointURI())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("grant_type", "authorization_code")
                            .with("client_id", PUBLIC_CLIENT_ID)
                            .with("code", queryParams.get("code"))
                            .with("redirect_uri", clientRedirectURI.toString())
                            // PKCE specific parameter - code_verifier was secret, IdP knows the S256 hash
                            .with("code_verifier", codeVerifier.getValue())
                    )
                    .exchange()
                    .expectStatus().is2xxSuccessful()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .value(Matchers.hasKey("access_token"))
                    .value(Matchers.hasKey("id_token"))
                    .value(Matchers.hasEntry("token_type", "Bearer"))
                    .value(Matchers.hasEntry("scope", "openid email profile"))
                    .returnResult().getResponseBody();

            // try to auth-protected resource without bearer
            // redirects to /oauth2/authorization/keycloak
            // (but we would prefer an HTTP 401 with Accept:application/json)
            http.get().uri("/me")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().is3xxRedirection()
                    .expectHeader().location("/oauth2/authorization/keycloak");

            // request auth-protected resource with the jwt bearer token
            http.get().uri("/me")
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + tokenResponse.get("access_token"))
                    .exchange()
                    .expectStatus().is2xxSuccessful()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .value(Matchers.hasEntry("name", "alice"));
        }

        private static String extractFormActionFromHtml(EntityExchangeResult<byte[]> response) {
            var html = new String(Objects.requireNonNull(response.getResponseBody()), StandardCharsets.UTF_8);

            // first find the <form>
            int formStartIdx = html.indexOf("<form");
            int formEndIdx = html.indexOf("</form>");

            // sanity check: expecting a SINGLE <form /> on this page
            assertThat(formStartIdx).isGreaterThan(0);
            assertThat(formEndIdx).isGreaterThan(formStartIdx);
            assertThat(formStartIdx).isEqualTo(html.lastIndexOf("<form"));

            var formHtml = html.substring(formStartIdx, formEndIdx + "</form>".length());

            int actionIdx = formHtml.indexOf("action=\"");
            var action = formHtml.substring(actionIdx + "action=\"".length());
            action = action.substring(0, action.indexOf("\""));

            return HtmlUtils.htmlUnescape(action);
        }
    }

    @Nested
    class JwtBearerIntegrationTest {

    }
}

