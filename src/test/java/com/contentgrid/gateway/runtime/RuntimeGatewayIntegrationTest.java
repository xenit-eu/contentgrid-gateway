package com.contentgrid.gateway.runtime;

import static com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata.LABEL_APPLICATION_ID;
import static com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata.LABEL_DEPLOYMENT_ID;
import static com.contentgrid.gateway.runtime.web.ContentGridRuntimeHeaders.CONTENTGRID_APPLICATION_ID;
import static com.contentgrid.gateway.runtime.web.ContentGridRuntimeHeaders.CONTENTGRID_DEPLOYMENT_ID;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration.Keys;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationFragment;
import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.config.ComposedApplicationConfiguration;
import com.contentgrid.gateway.runtime.routing.ApplicationIdRequestResolver;
import com.contentgrid.gateway.runtime.routing.StaticVirtualHostApplicationIdResolver;
import com.contentgrid.gateway.security.jwt.issuer.AuthenticationInformationResolver;
import com.contentgrid.gateway.security.jwt.issuer.JwtSignerRegistry;
import com.contentgrid.gateway.security.oauth2.client.registration.DynamicReactiveClientRegistrationRepository;
import com.contentgrid.gateway.security.oidc.ReactiveClientRegistrationIdResolver;
import com.contentgrid.gateway.security.oidc.ReactiveClientRegistrationResolver;
import com.contentgrid.gateway.test.util.LoggingExchangeFilterFunction;
import com.contentgrid.thunx.pdp.PolicyDecisionPointClient;
import com.contentgrid.thunx.pdp.PolicyDecisions;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.nimbusds.jose.JWSAlgorithm.Family;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.OIDCClaimsRequest;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.InitializingBean;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.OidcLoginMutator;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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
                "contentgrid.gateway.jwt.signers.apps.active-keys: classpath:fixtures/internal-issuer.pem",
                "wiremock.reset-mappings-after-each-test: true"
        })
@AutoConfigureWireMock(port = 0)
@AutoConfigureWebTestClient
class RuntimeGatewayIntegrationTest {

    static final ApplicationId APP_ID = ApplicationId.random();
    static final DeploymentId DEPLOY_ID = DeploymentId.random();

    static final ApplicationId APP_ID_UNAVAILABLE = ApplicationId.random();
    static final ThunkExpression<Boolean> PARTIAL_EXPRESSION = Comparison.areEqual(
            SymbolicReference.parse("input.entity.public"),
            Scalar.of(true)
    );

    static final String OIDC_ISSUER = "https://auth.contentgrid.example/realms/abc";

    @Autowired
    WireMockServer wireMockServer;

    @Autowired
    PolicyDecisionPointClient<Authentication, ServerWebExchange> pdpClient;

    @TestConfiguration
    static class RuntimeTestConfiguration {

        @Bean
        @SuppressWarnings("unchecked")
        PolicyDecisionPointClient<Authentication, ServerWebExchange> pdpMockClient() {
            return (PolicyDecisionPointClient<Authentication, ServerWebExchange>) Mockito.mock(PolicyDecisionPointClient.class);
        }

        // workaround for wiremock logging - see https://github.com/spring-cloud/spring-cloud-contract/issues/1916
        // fixed in spring-cloud-contract-wiremock:4.0.5 (now:4.0.4)
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

        @Bean
        ReactiveOAuth2AuthorizedClientManager oAuth2AuthorizedClientManager(ReactiveClientRegistrationRepository clientRegistrationRepository) {
            return new DefaultReactiveOAuth2AuthorizedClientManager(
                    clientRegistrationRepository,
                    new WebSessionServerOAuth2AuthorizedClientRepository()
            );
        }

        @Bean
        ReactiveOAuth2UserService<OidcUserRequest, OidcUser> fakeOAuth2UserService() {
            return new OidcReactiveOAuth2UserService();
        }

        @Bean
        @Primary
        ReactiveClientRegistrationResolver fakeReactiveClientRegistrationResolver(ReactiveClientRegistrationIdResolver registrationIdResolver) {
            return applicationConfiguration -> registrationIdResolver.resolveRegistrationId(applicationConfiguration.getApplicationId())
                    .map(registrationId -> ClientRegistration.withRegistrationId(registrationId)
                            .userNameAttributeName(IdTokenClaimNames.SUB)
                            .issuerUri(applicationConfiguration.getIssuerUri())
                            .clientId(applicationConfiguration.getClientId())
                            .clientSecret(applicationConfiguration.getClientSecret())
                            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                            .redirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}")
                            .authorizationUri("http://example.invalid/authorize")
                            .tokenUri("http://example.invalid/token")
                    .build());
        }

        @Bean
        InitializingBean initializeApplicationConfiguration(ComposableApplicationConfigurationRepository applicationConfigurationRepository) {
            return () -> {
                applicationConfigurationRepository.put(new ComposedApplicationConfiguration(APP_ID)
                        .withAdditionalConfiguration(new ApplicationConfigurationFragment(APP_ID+"-config", APP_ID, Map.of(
                                Keys.ROUTING_DOMAINS, hostname(APP_ID),
                                Keys.ISSUER_URI, OIDC_ISSUER,
                                Keys.CLIENT_ID, "mock-client-id",
                                Keys.CLIENT_SECRET, "secret"
                        )))
                );
                applicationConfigurationRepository.put(new ComposedApplicationConfiguration(APP_ID_UNAVAILABLE)
                        .withAdditionalConfiguration(new ApplicationConfigurationFragment(APP_ID_UNAVAILABLE+"-config", APP_ID_UNAVAILABLE, Map.of(
                                Keys.ROUTING_DOMAINS, hostname("unavailable"),
                                Keys.ISSUER_URI, OIDC_ISSUER,
                                Keys.CLIENT_ID, "mock-client-id",
                                Keys.CLIENT_SECRET, "secret"
                        )))
                );
            };
        }
    }

    @Autowired
    ReactiveClientRegistrationRepository clientRegistrationRepository;
    @Autowired
    ReactiveClientRegistrationIdResolver clientRegistrationIdResolver;

    OidcLoginMutator mockOidcLoginWithIssuer() {
        return mockOidcLogin()
                .idToken(idToken -> idToken.issuer(OIDC_ISSUER))
                .clientRegistration(clientRegistrationRepository.findByRegistrationId(clientRegistrationIdResolver.resolveRegistrationId(APP_ID).block()).block());
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

    @Test
    void contentgridHeaders_expected() {
        wireMockServer.stubFor(WireMock.get("/test").willReturn(WireMock.ok("OK")));
        Mockito.when(pdpClient.conditional(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(PolicyDecisions.allowed()));

        webTestClient
                .mutateWith(mockOidcLoginWithIssuer())
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
        Mockito.when(pdpClient.conditional(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(PolicyDecisions.allowed()));

        webTestClient
                .mutateWith(mockOidcLoginWithIssuer())
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
        Mockito.when(pdpClient.conditional(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(PolicyDecisions.allowed()));

        webTestClient
                .mutateWith(mockOidcLoginWithIssuer())
                .get().uri("https://{hostname}/test", hostname)
                .header("Host", hostname)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK);

        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/test"))
                .withHeader(HOST, equalTo(hostname))
        );
    }

    @Test
    void abacContextHeader_expected_for_conditional_access() {
        var hostname = hostname(APP_ID);
        wireMockServer.stubFor(WireMock.get("/test").willReturn(WireMock.ok("OK")));
        Mockito.when(pdpClient.conditional(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(PolicyDecisions.conditional(PARTIAL_EXPRESSION)));

        webTestClient
                .mutateWith(mockOidcLoginWithIssuer())
                .get().uri("https://{hostname}/test", hostname)
                .header("Host", hostname)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK);

        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/test"))
                .withHeader("X-Abac-Context", WireMock.matching(".*"))
        );

    }

    @Test
    void internalJwt_expected_policy_allow(ApplicationContext applicationContext) {
        var hostname = hostname(APP_ID);
        wireMockServer.stubFor(WireMock.get("/test").willReturn(WireMock.ok("OK")));
        var appsSigner = applicationContext.getBean(JwtSignerRegistry.class).getRequiredSigner("apps");

        Mockito.when(pdpClient.conditional(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(PolicyDecisions.allowed()));

        webTestClient
                .mutateWith(mockOidcLoginWithIssuer())
                .get().uri("https://{hostname}/test", hostname)
                .header("Host", hostname)
                .exchange()
                .expectStatus()
                .isOk();
        var jwkSet = appsSigner.getSigningKeys();
        var internalTokenValidator = new IDTokenValidator(
                Issuer.parse(OIDC_ISSUER),
                new ClientID("contentgrid:app:"+ APP_ID +":"+DEPLOY_ID), // We are working from the perspective of the client application here

                new JWSVerificationKeySelector<>(Family.SIGNATURE, (selector, context) -> selector.select(jwkSet)),
                null
        );

        var requests = wireMockServer.findRequestsMatching(WireMock.getRequestedFor(WireMock.urlEqualTo("/test")).build());
        assertThat(requests.getRequests()).singleElement().satisfies(request -> {
            assertThat(request.getHeader("authorization")).satisfies(authorization -> {
                assertThat(authorization).startsWith("Bearer ");
                assertThat(authorization.replaceFirst("Bearer ", "")).satisfies(receivedJwt -> {
                    assertThat(SignedJWT.parse(receivedJwt)).satisfies(signedJwt -> {
                        assertThat(signedJwt.getJWTClaimsSet()).satisfies(claims -> {
                            assertThat(claims.getAudience()).containsExactly("contentgrid:app:"+ APP_ID+":"+DEPLOY_ID);
                            assertThat(claims.getIssuer()).isEqualTo(OIDC_ISSUER);
                            assertThat(claims.getSubject()).isEqualTo("user");
                        });
                        assertThatCode(() -> internalTokenValidator.validate(signedJwt, null))
                                .doesNotThrowAnyException();
                    });
                });
            });
        });
    }

    @Test
    void internalJwt_expected_policy_conditional(ApplicationContext applicationContext) {
        var hostname = hostname(APP_ID);
        wireMockServer.stubFor(WireMock.get("/test").willReturn(WireMock.ok("OK")));
        var appsSigner = applicationContext.getBean(JwtSignerRegistry.class).getRequiredSigner("apps");

        Mockito.when(pdpClient.conditional(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(PolicyDecisions.conditional(PARTIAL_EXPRESSION)));

        webTestClient
                .mutateWith(mockOidcLoginWithIssuer())
                .get().uri("https://{hostname}/test", hostname)
                .header("Host", hostname)
                .exchange()
                .expectStatus()
                .isOk();
        var jwkSet = appsSigner.getSigningKeys();
        var internalTokenValidator = new IDTokenValidator(
                Issuer.parse(OIDC_ISSUER),
                new ClientID("contentgrid:app:"+ APP_ID +":"+DEPLOY_ID), // We are working from the perspective of the client application here

                new JWSVerificationKeySelector<>(Family.SIGNATURE, (selector, context) -> selector.select(jwkSet)),
                null
        );

        var requests = wireMockServer.findRequestsMatching(WireMock.getRequestedFor(WireMock.urlEqualTo("/test")).build());
        assertThat(requests.getRequests()).singleElement().satisfies(request -> {
            assertThat(request.getHeader("authorization")).satisfies(authorization -> {
                assertThat(authorization).startsWith("Bearer ");
                assertThat(authorization.replaceFirst("Bearer ", "")).satisfies(receivedJwt -> {
                    assertThat(SignedJWT.parse(receivedJwt)).satisfies(signedJwt -> {
                        assertThat(signedJwt.getJWTClaimsSet()).satisfies(claims -> {
                            assertThat(claims.getAudience()).containsExactly("contentgrid:app:"+ APP_ID+":"+DEPLOY_ID);
                            assertThat(claims.getIssuer()).isEqualTo(OIDC_ISSUER);
                            assertThat(claims.getSubject()).isEqualTo("user");
                            assertThat(claims.getClaim("x-abac-context")).isNotNull().isInstanceOf(String.class);
                        });
                        assertThatCode(() -> internalTokenValidator.validate(signedJwt, null))
                                .doesNotThrowAnyException();
                    });
                });
            });
        });
    }

    @Test
    void http403_expected_for_denied_access() {
        var hostname = hostname(APP_ID);
        wireMockServer.stubFor(WireMock.get("/test").willReturn(WireMock.ok("OK")));
        Mockito.when(pdpClient.conditional(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(PolicyDecisions.denied()));

        webTestClient
                .mutateWith(mockOidcLoginWithIssuer())
                .get().uri("https://{hostname}/test", hostname)
                .header("Host", hostname)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);

        wireMockServer.verify(0, WireMock.getRequestedFor(WireMock.urlEqualTo("/test")));

    }

    @Test
    void unknown_domain_http404() {
        webTestClient
                // slightly fictive test: if the domain is unknown,
                // we would not know how to map to a Keycloak realm anyway
                .mutateWith(mockOidcLoginWithIssuer())

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
                .mutateWith(mockOidcLoginWithIssuer())
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
                // DISABLED: .mutateWith(mockOidcLoginWithIssuer())
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
