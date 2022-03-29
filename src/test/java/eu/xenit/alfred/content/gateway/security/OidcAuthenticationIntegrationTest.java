package eu.xenit.alfred.content.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.util.HtmlUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
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
    private static final String CLIENT_ID = "contentcloud-gateway";

    @Container
    private static final GenericContainer<?> keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:latest")
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
        registry.add(
                "spring.security.oauth2.client.provider.keycloak.issuer-uri",
                OidcAuthenticationIntegrationTest::keycloakIssuerUrl);
    }

    static String keycloakIssuerUrl() {
        return String.format("http://%s:%s/auth/realms/%s",
                keycloak.getHost(), keycloak.getMappedPort(8080), REALM_NAME);
    }

    @Test
    public void keycloakOIDC_redirectFlow() {
        var baseUri = URI.create("http://localhost:" + port);
        var rest = WebTestClient
                .bindToServer(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
                .baseUrl(baseUri.toString())
                .build();

        var initialResponse = rest.get().uri("/")
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().location("/oauth2/authorization/keycloak")
                .expectBody()
                .consumeWith(result -> log.info(result.toString()))
                .isEmpty();


        var initialRedirect = initialResponse.getResponseHeaders().getLocation();

        var redirectToKeycloakResponse = rest.get()
                .uri(Objects.requireNonNull(baseUri.resolve(initialRedirect)))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("location", location -> {
                    assertThat(location).startsWith(keycloakIssuerUrl());
                    assertThat(URI.create(location))
                            .hasParameter("response_type", "code")
                            .hasParameter("client_id", CLIENT_ID)
                            .hasParameter("scope", "openid profile email");
                })
                .expectCookie().exists("SESSION")
                .expectCookie().httpOnly("SESSION", true)
                .expectCookie().sameSite("SESSION", "Lax")
                .expectBody().isEmpty();

        var redirectToKeycloakUri = redirectToKeycloakResponse.getResponseHeaders().getLocation();
        var keycloakLoginFormResponse = rest.get()
                .uri(Objects.requireNonNull(redirectToKeycloakUri))
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectHeader().contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .expectBody().returnResult();
        var htmlForm = new String(Objects.requireNonNull(keycloakLoginFormResponse.getResponseBody()), StandardCharsets.UTF_8);

        // submit credentials to keycloak on the action-url
        var keycloakLoginResponse = rest.post()
                .uri(extractFormActionFromHtml(htmlForm))
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

        var sessionCookie = redirectToKeycloakResponse.getResponseCookies().getFirst("SESSION");
        var appCodeResponse = rest.get()
                .uri(Objects.requireNonNull(keycloakLoginResponse.getResponseHeaders().getLocation()))
                .cookie("SESSION", sessionCookie.getValue())
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().location("/")
                .expectBody().isEmpty();


        var newSessionCookie = appCodeResponse.getResponseCookies().getFirst("SESSION");
        // session cookie _should_ update after login (avoiding session fixation issues, etc ..)
        assertThat(sessionCookie.getValue()).isNotEqualTo(newSessionCookie.getValue());
        sessionCookie = newSessionCookie;

        // now we can make authenticated requests with the session cookie !!
        rest.get().uri("/me")
                .cookie("SESSION", sessionCookie.getValue())
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .consumeWith(result -> log.info(result.toString()))
                .jsonPath("$.name").isEqualTo("alice");
    }

    private static String extractFormActionFromHtml(String keycloakBody) {
        // first find the <form>
        int formStartIdx = keycloakBody.indexOf("<form");
        int formEndIdx = keycloakBody.indexOf("</form>");

        // sanity check: expecting a SINGLE <form /> on this page
        assertThat(formStartIdx).isGreaterThan(0);
        assertThat(formEndIdx).isGreaterThan(formStartIdx);
        assertThat(formStartIdx).isEqualTo(keycloakBody.lastIndexOf("<form"));

        var formHtml = keycloakBody.substring(formStartIdx, formEndIdx + "</form>" .length());

        int actionIdx = formHtml.indexOf("action=\"");
        var action = formHtml.substring(actionIdx + "action=\"" .length());
        action = action.substring(0, action.indexOf("\""));

        return HtmlUtils.htmlUnescape(action);
    }
}

