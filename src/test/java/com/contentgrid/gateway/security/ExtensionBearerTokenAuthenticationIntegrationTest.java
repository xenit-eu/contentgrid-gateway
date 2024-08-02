package com.contentgrid.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.configuration.api.fragments.ConfigurationFragment;
import com.contentgrid.configuration.api.fragments.DynamicallyConfigurable;
import com.contentgrid.configuration.applications.ApplicationConfiguration;
import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.routing.ApplicationIdRequestResolver;
import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.jwt.issuer.JwtClaimsSigner;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.TextEncryptorFactory;
import com.contentgrid.gateway.test.security.FakeBase64TextEncryptorFactory;
import com.contentgrid.gateway.test.security.TestAuthenticationDetails;
import com.contentgrid.gateway.test.security.jwt.SingleKeyJwtClaimsSigner;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.RequestHeadersSpec;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "contentgrid.gateway.runtime-platform.enabled=true",
        }
)
@Slf4j
class ExtensionBearerTokenAuthenticationIntegrationTest extends AbstractKeycloakIntegrationTest {

    private static final JwtClaimsSigner JWT_SIGNER = new SingleKeyJwtClaimsSigner();

    private static final WireMockServer wireMockServer = new WireMockServer(
            new WireMockConfiguration().dynamicPort());
    private static final String EXTENSION_SYSTEM_ISSUER = "https://extensions.invalid/authentication/system";
    private static final String EXTENSION_DELEGATE_ISSUER = "https://extensions.invalid/authentication/delegated";


    @Value("${local.server.port}")
    private int port;

    private static Realm realm;
    private static PublicClientRegistration client;

    @Autowired
    DynamicallyConfigurable<String, ApplicationId, ApplicationConfiguration> applicationConfigurationRepository;

    @Autowired
    @Qualifier("contentgrid.gateway.runtime-platform.endpoints.authentication.encryption.TextEncryptorFactory")
    TextEncryptorFactory authenticationTextEncryptorFactory;

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

        @Bean(name = "contentgrid.gateway.runtime-platform.endpoints.authentication.encryption.TextEncryptorFactory")
        @Primary
        TextEncryptorFactory textEncryptorFactory() {
            return new FakeBase64TextEncryptorFactory();
        }
    }

    @BeforeAll
    static void startWiremock() {
        wireMockServer.start();
        wireMockServer.stubFor(WireMock.get("/jwks").willReturn(WireMock.okJson(JWT_SIGNER.getSigningKeys().toString())));
        realm = createRealm("user-authentication");
        client = createPublicClient(realm, "gateway", null);
    }

    @AfterAll
    static void shutdownWiremock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("contentgrid.gateway.runtime-platform.external-issuers.extension-system.issuer", () -> EXTENSION_SYSTEM_ISSUER);
        registry.add("contentgrid.gateway.runtime-platform.external-issuers.extension-system.jwk-set-uri", () -> wireMockServer.baseUrl()+"/jwks");
        registry.add("contentgrid.gateway.runtime-platform.external-issuers.extension-delegation.issuer", () -> EXTENSION_DELEGATE_ISSUER);
        registry.add("contentgrid.gateway.runtime-platform.external-issuers.extension-delegation.jwk-set-uri", () -> wireMockServer.baseUrl()+"/jwks");
    }

    @Test
    void authenticate_extension_system_jwt() {
        // create gateway app configuration
        var appId = ApplicationId.random();
        applicationConfigurationRepository.register(new ConfigurationFragment<>(
                "config-id",
                appId,
                ApplicationConfiguration.builder()
                        .clientId(client.clientId())
                        .issuerUri(realm.getIssuerUrl())
                        .build()
        ));

        var bearerToken = createBearerToken(Map.of(
                JwtClaimNames.ISS, EXTENSION_SYSTEM_ISSUER,
                JwtClaimNames.SUB, "extension123",
                JwtClaimNames.IAT, Instant.now().getEpochSecond(),
                JwtClaimNames.EXP, Instant.now().plus(5, ChronoUnit.MINUTES).getEpochSecond(),
                JwtClaimNames.AUD, "contentgrid:application:"+appId
        ));

        assertRequest_withBearer(appId, bearerToken)
                .expectStatus().is2xxSuccessful()
                .expectBody(TestAuthenticationDetails.class)
                .value(authenticationDetails -> {
                    assertThat(authenticationDetails.getPrincipal().getType()).isEqualTo(ActorType.EXTENSION);
                    assertThat(authenticationDetails.getPrincipal().getClaims().getClaims())
                            .containsExactlyInAnyOrderEntriesOf(Map.of(
                                    JwtClaimNames.ISS, EXTENSION_SYSTEM_ISSUER,
                                    JwtClaimNames.SUB, "extension123"
                            ));
                    assertThat(authenticationDetails.getActor()).isNull();
                });
        applicationConfigurationRepository.revoke("config-id");
    }

    @Test
    void reject_extension_system_jwt_invalid_audience() {
        // create gateway app configuration
        var appId = ApplicationId.random();
        applicationConfigurationRepository.register(new ConfigurationFragment<>(
                "config-id",
                appId,
                ApplicationConfiguration.builder()
                        .clientId(client.clientId())
                        .issuerUri(realm.getIssuerUrl())
                        .build()
        ));

        var bearerToken = createBearerToken(Map.of(
                JwtClaimNames.ISS, EXTENSION_SYSTEM_ISSUER,
                JwtClaimNames.SUB, "extension123",
                JwtClaimNames.IAT, Instant.now().getEpochSecond(),
                JwtClaimNames.EXP, Instant.now().plus(5, ChronoUnit.MINUTES).getEpochSecond(),
                JwtClaimNames.AUD, "contentgrid:application:"+ApplicationId.random()
        ));

        assertRequest_withBearer(appId, bearerToken)
                .expectStatus().is4xxClientError();

        applicationConfigurationRepository.revoke("config-id");
    }

    @Test
    void authenticate_delegated_jwt() {
        // create gateway app configuration
        var appId = ApplicationId.random();
        applicationConfigurationRepository.register(new ConfigurationFragment<>(
                "config-id",
                appId,
                ApplicationConfiguration.builder()
                        .clientId(client.clientId())
                        .issuerUri(realm.getIssuerUrl())
                        .build()
        ));

        var bearerToken = createBearerToken(Map.of(
                JwtClaimNames.ISS, EXTENSION_DELEGATE_ISSUER,
                JwtClaimNames.SUB, realm.getIssuerUrl()+"#my-user-123",
                JwtClaimNames.IAT, Instant.now().getEpochSecond(),
                JwtClaimNames.EXP, Instant.now().plus(5, ChronoUnit.MINUTES).getEpochSecond(),
                JwtClaimNames.AUD, "contentgrid:application:"+appId,
                "restrict:principal_claims", authenticationTextEncryptorFactory.newEncryptor().encrypt("""
                            { "iss": "%s", "sub": "my-user-123", "contentgrid:claim1": "value2" }
                            """.formatted(realm.getIssuerUrl())),
                "act", Map.of(
                        JwtClaimNames.ISS, EXTENSION_SYSTEM_ISSUER,
                        JwtClaimNames.SUB, "extension123"
                )
        ));

        assertRequest_withBearer(appId, bearerToken)
                .expectStatus().is2xxSuccessful()
                .expectBody(TestAuthenticationDetails.class)
                .value(authenticationDetails -> {
                    assertThat(authenticationDetails.getPrincipal().getType()).isEqualTo(ActorType.USER);
                    assertThat(authenticationDetails.getPrincipal().getClaims().getClaims())
                            .containsExactlyInAnyOrderEntriesOf(Map.of(
                                    JwtClaimNames.ISS, realm.getIssuerUrl(),
                                    JwtClaimNames.SUB, "my-user-123",
                                    "contentgrid:claim1", "value2"
                            ));
                    assertThat(authenticationDetails.getActor().getType()).isEqualTo(ActorType.EXTENSION);
                    assertThat(authenticationDetails.getActor().getClaims().getClaims())
                            .containsExactlyInAnyOrderEntriesOf(Map.of(
                                    JwtClaimNames.ISS, EXTENSION_SYSTEM_ISSUER,
                                    JwtClaimNames.SUB, "extension123"
                            ));
                });
        applicationConfigurationRepository.revoke("config-id");
    }

    @Test
    void reject_delegated_jwt_invalid_audience() {
        // create gateway app configuration
        var appId = ApplicationId.random();
        applicationConfigurationRepository.register(new ConfigurationFragment<>(
                "config-id",
                appId,
                ApplicationConfiguration.builder()
                        .clientId(client.clientId())
                        .issuerUri(realm.getIssuerUrl())
                        .build()
        ));


        var bearerToken = createBearerToken(Map.of(
                JwtClaimNames.ISS, EXTENSION_DELEGATE_ISSUER,
                JwtClaimNames.SUB, realm.getIssuerUrl()+"#my-user-123",
                JwtClaimNames.IAT, Instant.now().getEpochSecond(),
                JwtClaimNames.EXP, Instant.now().plus(5, ChronoUnit.MINUTES).getEpochSecond(),
                JwtClaimNames.AUD, "contentgrid:application:"+ApplicationId.random(),
                "restrict:principal_claims", authenticationTextEncryptorFactory.newEncryptor().encrypt("""
                            { "iss": "%s", "sub": "my-user-123", "contentgrid:claim1": "value2" }
                            """.formatted(realm.getIssuerUrl())),
                "act", Map.of(
                        JwtClaimNames.ISS, EXTENSION_SYSTEM_ISSUER,
                        JwtClaimNames.SUB, "extension123"
                )
        ));

        assertRequest_withBearer(appId, bearerToken)
                .expectStatus().is4xxClientError()
        ;
        applicationConfigurationRepository.revoke("config-id");
    }
    @SneakyThrows
    private static String createBearerToken(Map<String, Object> rawJwtClaims) {
        return JWT_SIGNER.sign(JWTClaimsSet.parse(rawJwtClaims)).serialize();
    }

    private @NonNull WebTestClient.ResponseSpec assertRequest_withBearer(ApplicationId appId, String accessToken) {
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

    @NonNull
    private WebTestClient.ResponseSpec assertRequest(
            Consumer<RequestHeadersSpec<? extends RequestHeadersSpec<?>>> customizer) {
        var request = this.http.get()
                .uri("http://localhost:%s/_test/authenticationDetails".formatted(this.port));

        customizer.accept(request);

        return request.exchange();
    }

}
