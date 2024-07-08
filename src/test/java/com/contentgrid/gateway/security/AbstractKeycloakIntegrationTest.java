package com.contentgrid.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.test.security.ClaimAccessorMixin;
import com.contentgrid.gateway.test.util.LoggingExchangeFilterFunction;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response.Status.Family;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matchers;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Testcontainers
abstract class AbstractKeycloakIntegrationTest {

    @Container
    private static final KeycloakContainer KEYCLOAK = new KeycloakContainer().withContextPath("/");

    static URI keycloakServerUrl() {
        return URI.create(KEYCLOAK.getAuthServerUrl());
    }

    static Keycloak getKeycloakAdminClient() {
        return KEYCLOAK.getKeycloakAdminClient();
    }

    protected final WebTestClient http = WebTestClient
            .bindToServer(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
            .filter(new LoggingExchangeFilterFunction(log::info))
            .responseTimeout(Duration.ofHours(1)) // for interactive debugging
            .codecs(clientCodecConfigurer -> {
                clientCodecConfigurer.defaultCodecs().enableLoggingRequestDetails(true);
                clientCodecConfigurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(
                        Jackson2ObjectMapperBuilder.json()
                                .mixIn(ClaimAccessor.class, ClaimAccessorMixin.class)
                                .build()));
            })
            .build();

    @NonNull
    static Realm createRealm(String name) {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(name);
        realm.setEnabled(true);

        try (var client = getKeycloakAdminClient()) {
            client.realms().create(realm);
        }
        return new Realm(name);
    }


    @Value
    static class Realm {

        String name;

        String getIssuerUrl() {
            return keycloakServerUrl().resolve("realms/").resolve(name).toString();
        }

        Issuer getIssuer() {
            return new Issuer(this.getIssuerUrl());
        }
    }

    static ConfidentialCientRegistration createConfidentialClient(@NonNull Realm realm, @NonNull String clientId,
            @NonNull String redirectUri) {
        return createConfidentialClient(realm, clientId, UUID.randomUUID().toString(), redirectUri);
    }

    static ConfidentialCientRegistration createConfidentialClient(@NonNull Realm realm, @NonNull String clientId,
            String clientSecret, @NonNull String redirectUri) {
        var clientRepresentation = new ClientRepresentation();
        clientRepresentation.setClientId(clientId);
        clientRepresentation.setSecret(clientSecret);
        clientRepresentation.setStandardFlowEnabled(true);
        clientRepresentation.setPublicClient(false);

        clientRepresentation.setRedirectUris(List.of(redirectUri));
        clientRepresentation.setWebOrigins(List.of("+"));
        clientRepresentation.setAttributes(Map.of(
                "post.logout.redirect.uris", "+"
        ));
        clientRepresentation.setFullScopeAllowed(false);

        try (var keycloakClient = getKeycloakAdminClient()) {
            try (var response = keycloakClient.realm(realm.getName()).clients().create(clientRepresentation)) {
                assertThat(response.getStatusInfo().getFamily()).isEqualTo(Family.SUCCESSFUL);
                return new ConfidentialCientRegistration(clientId, clientRepresentation.getSecret(), redirectUri);
            }
        }
    }

    record ConfidentialCientRegistration(String clientId, String clientSecret, String redirectUri) {

    }

    static PublicClientRegistration createPublicClient(@NonNull Realm realm, @NonNull String clientId,
            String redirectUri) {
        var clientRepresentation = new ClientRepresentation();

        clientRepresentation.setClientId(clientId);
        clientRepresentation.setStandardFlowEnabled(true);
        clientRepresentation.setPublicClient(true);

        if (redirectUri != null) {
            clientRepresentation.setRedirectUris(List.of(redirectUri));
        }
        clientRepresentation.setWebOrigins(List.of("+"));
        clientRepresentation.setFullScopeAllowed(false);

        try (var keycloakClient = getKeycloakAdminClient()) {
            try (var response = keycloakClient.realm(realm.getName()).clients().create(clientRepresentation)) {
                assertThat(response.getStatusInfo().getFamily()).isEqualTo(Family.SUCCESSFUL);
                return new PublicClientRegistration(clientId, redirectUri);
            }
        }
    }

    record PublicClientRegistration(@NonNull String clientId, String redirectUri) {

    }

    static UserCredentials createUser(Realm realm, String username) {
        try (var keycloak = getKeycloakAdminClient()) {

            var userRepresentation = new UserRepresentation();
            userRepresentation.setUsername(username);
            userRepresentation.setEnabled(true);

            String userId;
            try (var user = keycloak.realm(realm.getName()).users().create(userRepresentation)) {
                userId = CreatedResponseUtil.getCreatedId(user);
            }

            var password = UUID.randomUUID().toString();
            var passwordRepresentation = new CredentialRepresentation();
            passwordRepresentation.setType("password");
            passwordRepresentation.setTemporary(false);
            passwordRepresentation.setValue(password);

            keycloak.realm(realm.getName()).users()
                    .get(userId)
                    .resetPassword(passwordRepresentation);

            return new UserCredentials(username, password, userId);
        }
    }

    record UserCredentials(String username, String password, String userId) {

    }

    @NonNull
    PkceAuthorizationCodeRequest createPkceAuthorizationCodeRequest(URI authorizationEndpointURI,
            PublicClientRegistration client) {
        // code-verifier is a random string generated by the client
        var codeVerifier = new CodeVerifier();
        // code-challenge is a SHA-256 hash of the code-verifier
        var codeChallenge = CodeChallenge.compute(CodeChallengeMethod.S256, codeVerifier);
        // random string to link the callback to the authorization request (XSS protection)
        var state = new State();

        // the OIDC authorization request: get an authorization 'code'
        var authorizationCodeRequestUri = UriComponentsBuilder.fromUri(authorizationEndpointURI)
                .queryParam("client_id", client.clientId())
                .queryParam("redirect_uri", client.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email roles")
                .queryParam("state", state.getValue())
                // PKCE specific parameters
                .queryParam("code_challenge", codeChallenge.getValue())
                .queryParam("code_challenge_method", CodeChallengeMethod.S256.getValue())
                .build().toUri();

        return new PkceAuthorizationCodeRequest(authorizationCodeRequestUri, codeVerifier);
    }

    record PkceAuthorizationCodeRequest(@Getter @NonNull URI uri, @Getter @NonNull CodeVerifier codeVerifier) {

    }

    @NonNull
    ConfidentialAuthorizationCodeRequest getConfidentialAuthorizationCodeRequest(
            ConfidentialCientRegistration client, ApplicationId appId) {
        // try to access a protected resource
        var protectedUri = URI.create(client.redirectUri()).resolve("/me");
        var request = this.http.get().uri(protectedUri)
                .accept(MediaType.TEXT_HTML);

        if (appId != null) {
            request.header("Test-ApplicationId", appId.getValue());
        }

        var authorizationRequestInitUri = request.exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("location", loc ->
                        assertThat(new UriTemplate("/oauth2/authorization/{registrationId}").matches(loc)).isTrue())
                .expectBody().isEmpty()
                .getResponseHeaders().getLocation();

        // TODO try to fiddle with this authorizationRedirectLocation redirectURI (registrationId)
        // follow the authz code request init redirectURI redirect
        // this is handled by the Gateway OAuth2AuthorizationRequestRedirectWebFilter
        var authzCodeRequest = this.http.get()
                .uri(URI.create(client.redirectUri).resolve(Objects.requireNonNull(authorizationRequestInitUri)));

        if (appId != null) {
            authzCodeRequest.header("Test-ApplicationId", appId.getValue());
        }

        var authzCodeRequestRedirect = authzCodeRequest.exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("location", location -> {
                    assertThat(URI.create(location))
                            .hasParameter("response_type", "code")
                            .hasParameter("client_id", client.clientId())
                            .hasParameter("scope", "openid profile email");
                })
                .expectCookie().exists("SESSION")
                .expectCookie().httpOnly("SESSION", true)
                .expectCookie().sameSite("SESSION", "Lax")
                .expectBody().isEmpty();

        return new ConfidentialAuthorizationCodeRequest(
                Objects.requireNonNull(authzCodeRequestRedirect.getResponseHeaders().getLocation()),
                Objects.requireNonNull(authzCodeRequestRedirect.getResponseCookies().toSingleValueMap().get("SESSION")).getValue()
        );
    }

    record ConfidentialAuthorizationCodeRequest(@Getter @NonNull URI uri, @Getter @NonNull String sessionCookie) {

    }

    record AuthorizationCodeResponse(@Getter URI redirectURI) {
        public Optional<AuthorizationCode> getCode() {
            return Optional.ofNullable(this.redirectURI)
                    .map(UriComponentsBuilder::fromUri)
                    .map(UriComponentsBuilder::build)
                    .map(UriComponents::getQueryParams)
                    .map(params -> params.getFirst("code"))
                    .map(AuthorizationCode::new);
        }

    }


    @NonNull
    AuthorizationCodeResponse getAuthorizationCodeResponse(URI authorizationCodeRequest,
            UserCredentials keycloakCredentials) {

        // authorization code request, response contains an html login-form
        var keycloakLoginFormResponse = this.http.get()
                .uri(authorizationCodeRequest)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectHeader().contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .expectBody(String.class).returnResult();
        var keycloakLoginForm = new KeycloakLoginForm(keycloakLoginFormResponse);

        // login with Keycloak using username & password
        // response is a redirect with a 'code' as query parameter
        var keycloakLoginResponse = this.http.post()
                .uri(keycloakLoginForm.getFormActionUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .cookies(cookies -> {
                    keycloakLoginForm.getResponseCookies().forEach((name, values) -> {
                        values.stream().map(HttpCookie::getValue).forEach(val -> cookies.add(name, val));
                    });
                })
                .body(BodyInserters
                        .fromFormData("username", keycloakCredentials.username())
                        .with("password", keycloakCredentials.password())
                        .with("credentialId", ""))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("location", location -> {
                    assertThat(URI.create(location)).hasParameter("code");
                })
                .expectBody().isEmpty();

        var location = Objects.requireNonNull(keycloakLoginResponse.getResponseHeaders().getLocation());
        return new AuthorizationCodeResponse(location);
    }

    TokenResponse completeTokenExchange(PublicClientRegistration publicClient, URI tokenEndpointURI,
            AuthorizationCodeResponse authzCodeResponse, CodeVerifier codeVerifier) {

        var http = WebTestClient
                .bindToServer(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
                .build();

        var tokenResponse = http.post()
                .uri(tokenEndpointURI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "authorization_code")
                        .with("client_id", publicClient.clientId())
                        .with("code", authzCodeResponse.getCode().orElseThrow().getValue())
                        .with("redirect_uri", publicClient.redirectUri())
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
                .returnResult().getResponseBody();

        return new TokenResponse(tokenResponse);
    }

    ResponseCookie completeOAuth2Login(String session, AuthorizationCodeResponse authzCodeResponse, ApplicationId appId) {
        // follow the authzCodeResponse redirectURI  back to the gateway + SESSION cookie from the authzCodeRequest
        var request = this.http.get()
                .uri(authzCodeResponse.redirectURI())
                .cookie("SESSION", session);

        if (appId != null) {
            request.header("Test-ApplicationId", appId.getValue());
        }

        var authorizationCodeResponse = request.exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().location("/")
                .expectCookie().exists("SESSION")
                .expectBody().isEmpty();

        return authorizationCodeResponse.getResponseCookies().toSingleValueMap().get("SESSION");
    }

    @RequiredArgsConstructor
    static class TokenResponse {

        private final Map<String, Object> response;

        public boolean isSuccess() {
            return this.getAccessToken() != null;
        }

        public String getAccessToken() {
            return (String) this.get("access_token");
        }

        public Object get(String key) {
            return this.response.get(key);
        }

    }


    @RequiredArgsConstructor
    static class KeycloakLoginForm {

        @NonNull
        private final EntityExchangeResult<String> response;

        URI getFormActionUri() {
            var html = response.getResponseBody();
            assertThat(html).isNotBlank();

            // first find the <form>
            int formStartIdx = html.indexOf("<form");
            int formEndIdx = html.indexOf("</form>");

            // sanity check: expecting a SINGLE <form /> on this page
            assertThat(formStartIdx).isPositive();
            assertThat(formEndIdx).isGreaterThan(formStartIdx);
            assertThat(formStartIdx).isEqualTo(html.lastIndexOf("<form"));

            var formHtml = html.substring(formStartIdx, formEndIdx + "</form>".length());

            int actionIdx = formHtml.indexOf("action=\"");
            var action = formHtml.substring(actionIdx + "action=\"".length());
            action = action.substring(0, action.indexOf("\""));

            return URI.create(HtmlUtils.htmlUnescape(action));
        }

        public MultiValueMap<String, ResponseCookie> getResponseCookies() {
            return response.getResponseCookies();
        }
    }


}
