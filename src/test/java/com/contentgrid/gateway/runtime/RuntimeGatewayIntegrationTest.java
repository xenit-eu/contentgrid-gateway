package com.contentgrid.gateway.runtime;

import static com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata.LABEL_APPLICATION_ID;
import static com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata.LABEL_DEPLOYMENT_ID;
import static com.contentgrid.gateway.runtime.web.ContentGridRuntimeHeaders.CONTENTGRID_APPLICATION_ID;
import static com.contentgrid.gateway.runtime.web.ContentGridRuntimeHeaders.CONTENTGRID_DEPLOYMENT_ID;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_HOST_HEADER;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_PORT_HEADER;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_PROTO_HEADER;
import static org.springframework.http.HttpHeaders.HOST;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import com.contentgrid.gateway.runtime.routing.RuntimeVirtualHostResolver;
import com.contentgrid.gateway.runtime.routing.StaticVirtualHostResolver;
import com.contentgrid.gateway.test.util.LoggingExchangeFilterFunction;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.cloud.contract.wiremock.WireMockConfigurationCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for ContentGrid Gateway using the Runtime profile.
 *
 * This integration test sets up a WireMock server on a random port on localhost as a backend service. This
 * WireMock server is then registered in the application catalog.
 *
 * Additionally, a (fake) domain is mapped to this service in the service catalog.
 *
 * The gateway is configured with the runtime Spring Security setup, requiring OIDC.
 *
 * A {@link WebTestClient} bean, bound to the {@link ApplicationContext} is provided, which bypasses the HTTP server
 * and uses mock requests and responses.
 * OIDC login can be simulated with WebTestClient using:
 *
 * <pre>
 * webTestClient
 *      .mutateWith(mockOidcLogin())
 *      .get().uri("/endpoint")
 *      .exchange();
 * </pre>
 */
@Slf4j
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {
                "contentgrid.gateway.runtime-platform.enabled: true",
                "wiremock.reset-mappings-after-each-test: true"
        })
@AutoConfigureWireMock(port = 0)
@AutoConfigureWebTestClient
class RuntimeGatewayIntegrationTest {

    static final ApplicationId APP_ID = ApplicationId.random();
    static final DeploymentId DEPLOY_ID = DeploymentId.random();

    static final ApplicationId APP_ID_UNAVAILABLE = ApplicationId.random();

    @Autowired
    WireMockServer wireMockServer;

    @TestConfiguration
    static class RuntimeTestConfiguration {

        @Bean
        @Primary
        RuntimeVirtualHostResolver staticVirtualHostResolver() {
            return new StaticVirtualHostResolver(Map.of(
                    hostname(APP_ID), APP_ID,
                    hostname("unavailable"),  APP_ID_UNAVAILABLE // this app has no deployments
            ));
        }

        // fix wiremock logging - see https://github.com/spring-cloud/spring-cloud-contract/issues/1916
        @Bean
        WireMockConfigurationCustomizer optionsCustomizer() {
            return config -> config.notifier(new ConsoleNotifier(true));
        }

        @Bean
        WebTestClient webTestClient(ApplicationContext context) {
            return WebTestClient.bindToApplicationContext(context)
                // setup Spring Security
                .apply(springSecurity())
                // configure request/response logging
                .configureClient()
                    .filter(new LoggingExchangeFilterFunction(log::info))
                // avoid timeouts during debugging sessions
                .responseTimeout(Duration.ofHours(1))
                .build();
        }

    }

    @BeforeAll
    public static void setup(@Autowired ServiceCatalog catalog, @Autowired WireMockServer wiremock) {
        // register the wire-mock-server in the service catalog
        catalog.handleServiceAdded(new DefaultServiceInstance(
                "instance-%s".formatted(UUID.randomUUID()),
                "wiremock-%s".formatted(wiremock.hashCode()),
                "localhost",
                wiremock.port(),
                false,
                Map.of(
                        LABEL_APPLICATION_ID, APP_ID.toString(),
                        LABEL_DEPLOYMENT_ID, DEPLOY_ID.toString())
        ));
    }

    @Autowired
    WebTestClient webTestClient;

    @Nested
    class HappyPath {

        @Test
        void contentgridHeaders_expected() {
            wireMockServer.stubFor(WireMock.get("/test").willReturn(WireMock.ok("OK")));

            webTestClient
                    .mutateWith(mockOidcLogin())
                    .get().uri("https://{hostname}/test", hostname(APP_ID))
                    .header("Host", hostname(APP_ID))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.OK)
                    .expectHeader().value(CONTENTGRID_APPLICATION_ID, is(APP_ID.toString()))
                    .expectHeader().value(CONTENTGRID_DEPLOYMENT_ID, is(DEPLOY_ID.toString()));

            wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/test")));
        }

        @Test
        void xForwardedHeaders_expected() {
            var hostname = hostname(APP_ID);
            wireMockServer.stubFor(WireMock.get("/test").willReturn(WireMock.ok("OK")));

            webTestClient
                    .mutateWith(mockOidcLogin())
                    .get().uri("https://{hostname}/test", hostname)
                    .header("Host", hostname)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.OK);

            wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/test"))
                    .withHeader(X_FORWARDED_HOST_HEADER, equalTo(hostname))
                    .withHeader(X_FORWARDED_PROTO_HEADER, equalTo("https"))
                    .withHeader(X_FORWARDED_PORT_HEADER, equalTo("443"))
            );
        }

        @Test
        void preserveHostHeader_expected() {
            var hostname = hostname(APP_ID);
            wireMockServer.stubFor(WireMock.get("/test").willReturn(WireMock.ok("OK")));

            webTestClient
                    .mutateWith(mockOidcLogin())
                    .get().uri("https://{hostname}/test", hostname)
                    .header("Host", hostname)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.OK);

            wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/test"))
                    .withHeader(HOST, equalTo(hostname))
            );
        }
    }

    @Test
    void unknown_domain_http404() {
        webTestClient
                // slightly fictive test: if the domain is unknown,
                // we would not know how to map to a Keycloak realm anyway
                .mutateWith(mockOidcLogin())

                .get().uri("https://{hostname}/test", hostname("unknown"))
                .header("Host", hostname("unknown"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectHeader().doesNotExist(CONTENTGRID_APPLICATION_ID)
                .expectHeader().doesNotExist(CONTENTGRID_DEPLOYMENT_ID);

        wireMockServer.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @Disabled("ACC-948 currently returns HTTP 404")
    void service_unavailable_http503() {
        // application is provisioned, but no deployments available in the service catalog
        webTestClient
                .mutateWith(mockOidcLogin())
                .get().uri("https://{hostname}/test", hostname("unavailable"))
                .header("Host", hostname("unknown"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().value(CONTENTGRID_APPLICATION_ID, is(APP_ID_UNAVAILABLE.toString()))
                .expectHeader().doesNotExist(CONTENTGRID_DEPLOYMENT_ID);

        wireMockServer.verify(0, anyRequestedFor(anyUrl()));
    }



    @Test
    void no_auth_http401() {
        wireMockServer.stubFor(WireMock.get("/test").willReturn(WireMock.ok()));

        webTestClient
                // DISABLED: .mutateWith(mockOidcLogin())
                .get().uri("https://{hostname}/test", hostname(APP_ID))
                .header("Host", hostname(APP_ID))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectHeader().value(CONTENTGRID_APPLICATION_ID, is(APP_ID.toString()))
                .expectHeader().value(CONTENTGRID_DEPLOYMENT_ID, is(DEPLOY_ID.toString()));

        wireMockServer.verify(0, anyRequestedFor(anyUrl()));
    }

    private static String hostname(@NonNull ApplicationId appId) {
        return hostname(appId.toString());
    }

    private static String hostname(@NonNull String prefix) {
        return "%s.cloud.contentgrid.invalid".formatted(prefix);
    }

}