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

import com.contentgrid.configuration.applications.ApplicationConfiguration;
import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.config.StaticApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.routing.ApplicationIdRequestResolver;
import com.contentgrid.gateway.runtime.routing.StaticVirtualHostApplicationIdResolver;
import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.authority.DelegatedAuthenticationDetailsGrantedAuthority;
import com.contentgrid.gateway.security.authority.PrincipalAuthenticationDetailsGrantedAuthority;
import com.contentgrid.gateway.security.jwt.issuer.JwtSignerRegistry;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.PropertiesBasedTextEncryptorFactory;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.PropertiesBasedTextEncryptorFactory.TextEncryptorProperties;
import com.contentgrid.gateway.test.util.LoggingExchangeFilterFunction;
import com.contentgrid.thunx.pdp.PolicyDecisionPointClient;
import com.contentgrid.thunx.pdp.PolicyDecisions;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jose.JWSAlgorithm.Family;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.OidcLoginMutator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;

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
                "contentgrid.gateway.runtime-platform.endpoints.authentication.encryption.active-keys: classpath:fixtures/authentication-encryption.bin",
                "contentgrid.gateway.runtime-platform.endpoints.authentication.authorization: authenticated",
                "contentgrid.gateway.jwt.signers.apps.active-keys: classpath:fixtures/internal-issuer.pem",
                "contentgrid.gateway.jwt.signers.authentication.active-keys: classpath:fixtures/authentication-issuer.pem",
                "wiremock.reset-mappings-after-each-test: true"
        })
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
    public static final String EXTENSION_SYSTEM_ISSUER = "https://extensions.invalid/authentication/system";

    static WireMockServer wireMockServer = new WireMockServer(new WireMockConfiguration().dynamicPort());

    @Autowired
    PolicyDecisionPointClient<Authentication, ServerWebExchange> pdpClient;

    @TestConfiguration
    static class RuntimeTestConfiguration {

        @Bean
        @Primary
        ApplicationIdRequestResolver applicationIdRequestResolver() {
            return new StaticVirtualHostApplicationIdResolver(Map.of(
                    hostname(APP_ID), APP_ID,
                    hostname("unavailable"),  APP_ID_UNAVAILABLE // this app has no deployments
            ));
        }

        @Bean
        @Primary
        ApplicationConfigurationRepository staticApplicationConfigurationRepository() {
            return new StaticApplicationConfigurationRepository(Map.of(
                    APP_ID, ApplicationConfiguration.builder()
                            .issuerUri(OIDC_ISSUER)
                            .routingDomain(hostname(APP_ID))
                            .build(),
                    APP_ID_UNAVAILABLE, ApplicationConfiguration.builder()
                            .issuerUri(OIDC_ISSUER)
                            .routingDomain(hostname("unavailable"))
                            .build()
            ));
        }

        @Bean
        @SuppressWarnings("unchecked")
        PolicyDecisionPointClient<Authentication, ServerWebExchange> pdpMockClient() {
            return (PolicyDecisionPointClient<Authentication, ServerWebExchange>) Mockito.mock(PolicyDecisionPointClient.class);
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

    static OidcLoginMutator mockOidcLoginWithIssuer() {
        return mockOidcLogin()
                .idToken(idToken -> idToken.issuer(OIDC_ISSUER))
                .authorities(new PrincipalAuthenticationDetailsGrantedAuthority(new Actor(
                        ActorType.USER,
                        () -> Map.of(
                                JwtClaimNames.ISS, OIDC_ISSUER,
                                JwtClaimNames.SUB, "user"
                        ),
                        null
                )));
    }

    @BeforeAll
    static void startWiremock() {
        wireMockServer.start();
    }

    @BeforeEach
    void resetWiremock() {
        wireMockServer.resetAll();
    }

    @AfterEach
    void resetPdpClientMock() {
        Mockito.reset(pdpClient);
    }

    @AfterAll
    static void shutdownWiremock() {
        wireMockServer.stop();
    }

    @BeforeAll
    public static void setup(@Autowired ServiceCatalog catalog) {
        // register the wire-mock-server in the service catalog
        catalog.handleServiceAdded(new DefaultServiceInstance(
                "instance-%s".formatted(UUID.randomUUID()),
                "wiremock-%s".formatted(wireMockServer.hashCode()),
                "localhost",
                wireMockServer.port(),
                false,
                Map.of(
                        LABEL_APPLICATION_ID, APP_ID.toString(),
                        LABEL_DEPLOYMENT_ID, DEPLOY_ID.toString())
        ));
    }

    @Autowired
    WebTestClient webTestClient;

    @DynamicPropertySource
    static void configureMockServerPorts(DynamicPropertyRegistry registry) {
        registry.add("contentgrid.gateway.runtime-platform.endpoints.authentication.uri", () -> "http://localhost:"+wireMockServer.port());
    }

    static void assertBearerJwt(LoggedRequest request, ThrowingConsumer<SignedJWT> assertion) {
        assertThat(request.getHeader("authorization")).satisfies(authorization -> {
            assertThat(authorization).startsWith("Bearer ");
            assertThat(authorization.replaceFirst("Bearer ", "")).satisfies(receivedJwt -> {
                assertThat(SignedJWT.parse(receivedJwt)).satisfies(assertion);
            });
        });
    }

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
            assertBearerJwt(request, signedJwt -> {
                assertThat(signedJwt.getJWTClaimsSet()).satisfies(claims -> {
                    assertThat(claims.getAudience()).containsExactly("contentgrid:app:"+ APP_ID+":"+DEPLOY_ID);
                    assertThat(claims.getIssuer()).isEqualTo(OIDC_ISSUER);
                    assertThat(claims.getSubject()).isEqualTo("user");
                });
                assertThatCode(() -> internalTokenValidator.validate(signedJwt, null))
                        .doesNotThrowAnyException();
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
            assertBearerJwt(request, signedJwt -> {
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
    }

    @Test
    void authentication_endpoint_jwt(ApplicationContext applicationContext) {
        var hostname = hostname(APP_ID);
        wireMockServer.stubFor(WireMock.get("/.contentgrid/authentication/xyz").willReturn(WireMock.ok("OK")));

        // This should not affect this endpoint at all, authentication endpoint is configured to bypass OPA
        Mockito.when(pdpClient.conditional(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(PolicyDecisions.denied()));

        var authenticationSigner = applicationContext.getBean(JwtSignerRegistry.class).getRequiredSigner("authentication");
        var authenticationEncryptor = new PropertiesBasedTextEncryptorFactory(
                applicationContext,
                applicationContext.getBean("contentgrid.gateway.runtime-platform.endpoints.authentication.encryption", TextEncryptorProperties.class)
        );

        webTestClient
                .mutateWith(mockOidcLogin()
                        .authorities(new PrincipalAuthenticationDetailsGrantedAuthority(new Actor(
                        ActorType.USER,
                        () -> Map.of(
                                JwtClaimNames.ISS, OIDC_ISSUER,
                                JwtClaimNames.SUB, "user",
                                "contentgrid:my-claim", "xyz"
                        ),
                        null
                ))))
                .get().uri("https://{hostname}/.contentgrid/authentication/xyz", hostname)
                .header("Host", hostname)
                .exchange()
                .expectStatus()
                .isOk();

        Mockito.verifyNoInteractions(pdpClient);

        var jwkSet = authenticationSigner.getSigningKeys();
        var internalTokenValidator = new IDTokenValidator(
                Issuer.parse(OIDC_ISSUER),
                new ClientID("contentgrid:system:endpoints:authentication"), // We are working from the perspective of the client application here
                new JWSVerificationKeySelector<>(Family.SIGNATURE, (selector, context) -> selector.select(jwkSet)),
                null
        );


        var requests = wireMockServer.findRequestsMatching(WireMock.getRequestedFor(WireMock.urlEqualTo("/.contentgrid/authentication/xyz")).build());
        assertThat(requests.getRequests()).singleElement().satisfies(request -> {
            assertBearerJwt(request, signedJwt -> {
                assertThat(signedJwt.getJWTClaimsSet()).satisfies(claims -> {
                    assertThat(claims.getAudience()).containsExactly("contentgrid:system:endpoints:authentication");
                    assertThat(claims.getIssuer()).isEqualTo(OIDC_ISSUER);
                    assertThat(claims.getSubject()).isEqualTo("user");

                    assertThat(claims.getStringClaim("context:application:id")).isEqualTo(APP_ID.toString());
                    assertThat(claims.getStringListClaim("context:application:domains")).isEqualTo(List.of(hostname));

                    // Validate that arbitrary user claims are not leaked
                    assertThat(claims.getClaims()).doesNotContainKeys("contentgrid:my-claim", "other-claim");

                    // Validate encrypted claims to be the claims of the original token
                    assertThat(claims.getStringClaim("restrict:principal_claims")).satisfies(encryptedClaims -> {
                        assertThat(authenticationEncryptor.newEncryptor().decrypt(encryptedClaims)).satisfies(decrypted -> {
                            assertThat(new ObjectMapper().readValue(decrypted, new TypeReference<Map<String, Object>>() {
                            })).isEqualTo(Map.of(
                                    "iss", OIDC_ISSUER,
                                    "sub", "user",
                                    "contentgrid:my-claim", "xyz"
                            ));
                        });
                    });
                });
                assertThatCode(() -> internalTokenValidator.validate(signedJwt, null))
                        .doesNotThrowAnyException();;
            });
        });
    }

    @Test
    void authentication_endpoint_jwt_with_actors(ApplicationContext applicationContext) {
        var hostname = hostname(APP_ID);
        wireMockServer.stubFor(WireMock.get("/.contentgrid/authentication/xyz").willReturn(WireMock.ok("OK")));

        // This should not affect this endpoint at all, authentication endpoint is configured to bypass OPA
        Mockito.when(pdpClient.conditional(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(PolicyDecisions.denied()));

        var authenticationSigner = applicationContext.getBean(JwtSignerRegistry.class).getRequiredSigner("authentication");
        var authenticationEncryptor = new PropertiesBasedTextEncryptorFactory(
                applicationContext,
                applicationContext.getBean("contentgrid.gateway.runtime-platform.endpoints.authentication.encryption", TextEncryptorProperties.class)
        );

        webTestClient
                .mutateWith(mockOidcLogin()
                        .authorities(new DelegatedAuthenticationDetailsGrantedAuthority(new Actor(
                                ActorType.USER,
                                () -> Map.of(
                                        JwtClaimNames.ISS, OIDC_ISSUER,
                                        JwtClaimNames.SUB, "user",
                                        "contentgrid:my-claim", "xyz"
                                ),
                                null),
                                new Actor(
                                        ActorType.EXTENSION,
                                        () -> Map.of(
                                                "iss", EXTENSION_SYSTEM_ISSUER,
                                                "sub", "my-extension"
                                        ),
                                        new Actor(
                                                ActorType.EXTENSION,
                                                () -> Map.of(
                                                        "iss", EXTENSION_SYSTEM_ISSUER,
                                                        "sub", "other-extension"
                                                ),
                                                null
                                        )
                                ))))
                .get().uri("https://{hostname}/.contentgrid/authentication/xyz", hostname)
                .header("Host", hostname)
                .exchange()
                .expectStatus()
                .isOk();

        Mockito.verifyNoInteractions(pdpClient);

        var jwkSet = authenticationSigner.getSigningKeys();
        var internalTokenValidator = new IDTokenValidator(
                Issuer.parse(OIDC_ISSUER),
                new ClientID("contentgrid:system:endpoints:authentication"), // We are working from the perspective of the client application here
                new JWSVerificationKeySelector<>(Family.SIGNATURE, (selector, context) -> selector.select(jwkSet)),
                null
        );


        var requests = wireMockServer.findRequestsMatching(WireMock.getRequestedFor(WireMock.urlEqualTo("/.contentgrid/authentication/xyz")).build());
        assertThat(requests.getRequests()).singleElement().satisfies(request -> {
            assertBearerJwt(request, signedJwt -> {
                assertThat(signedJwt.getJWTClaimsSet()).satisfies(claims -> {
                    assertThat(claims.getAudience()).containsExactly("contentgrid:system:endpoints:authentication");
                    assertThat(claims.getIssuer()).isEqualTo(OIDC_ISSUER);
                    assertThat(claims.getSubject()).isEqualTo("user");

                    assertThat(claims.getStringClaim("context:application:id")).isEqualTo(APP_ID.toString());
                    assertThat(claims.getStringListClaim("context:application:domains")).isEqualTo(List.of(hostname));

                    // Validate that arbitrary user claims are not leaked
                    assertThat(claims.getClaims()).doesNotContainKeys("contentgrid:my-claim", "other-claim");

                    // Validate encrypted claims to be the claims of the original token
                    assertThat(claims.getStringClaim("restrict:principal_claims")).satisfies(encryptedClaims -> {
                        assertThat(authenticationEncryptor.newEncryptor().decrypt(encryptedClaims)).satisfies(decrypted -> {
                            assertThat(new ObjectMapper().readValue(decrypted, new TypeReference<Map<String, Object>>() {
                            })).isEqualTo(Map.of(
                                    "iss", OIDC_ISSUER,
                                    "sub", "user",
                                    "contentgrid:my-claim", "xyz"
                            ));
                        });
                    });

                    // Validate that actor chain is passed through
                    assertThat(claims.getJSONObjectClaim("act")).satisfies(actorClaim -> {
                        assertThat(actorClaim)
                                .containsEntry("iss", EXTENSION_SYSTEM_ISSUER)
                                .containsEntry("sub", "my-extension");
                        assertThat((Map<String, Object>)actorClaim.get("act")).containsExactlyInAnyOrderEntriesOf(Map.of(
                                "iss", EXTENSION_SYSTEM_ISSUER,
                                "sub", "other-extension"
                        ));
                    });
                });
                assertThatCode(() -> internalTokenValidator.validate(signedJwt, null))
                        .doesNotThrowAnyException();
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
        Mockito.when(pdpClient.conditional(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(PolicyDecisions.allowed()));
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
