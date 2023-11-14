package com.contentgrid.gateway.runtime.actuate;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration.Keys;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationFragment;
import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        properties = "contentgrid.gateway.runtime-platform.enabled=true",
        webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@AutoConfigureWireMock(port = 0)
class ContentGridActuatorEndpointTest {

    @LocalServerPort
    int port;

    @Autowired
    ComposableApplicationConfigurationRepository configRepo;

    @Autowired
    WebTestClient testClient;

    @Autowired
    WireMockServer wiremock;

    @BeforeEach
    void setup() {
        this.configRepo.clear();
    }

    @Test
    @WithMockUser
    void links() {
        testClient.get()
                .uri("http://localhost:" + port + "/actuator/contentgrid")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                            "_links": {
                                "applications": {
                                    "href": "/actuator/contentgrid/applications"
                                }
                            }
                        }
                        """);
    }

    @Test
    @WithMockUser
    void listApplications() {
        var appId = ApplicationId.random();

        this.configRepo.merge(ApplicationConfigurationFragment.from(appId, Map.of("foo", "bar")));

        testClient.get()
                .uri("http://localhost:" + port + "/actuator/contentgrid/applications")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                            "applications": [
                                {
                                    "id": "{APP_ID}",
                                    "_links": {
                                         "self": {
                                             "href": "/actuator/contentgrid/applications/{APP_ID}"
                                         },
                                         "config": {
                                             "href": "/actuator/contentgrid/applications/{APP_ID}/config"
                                         },
                                         "client-registration": {
                                             "href": "/actuator/contentgrid/applications/{APP_ID}/client-registration"
                                         }
                                     }
                                }
                            ]
                        }
                        """.replaceAll("\\{APP_ID}", appId.toString()));
    }

    @Test
    @WithMockUser
    void getApplicationById() {
        var appId = ApplicationId.random();

        this.configRepo.merge(ApplicationConfigurationFragment.from(appId, Map.of("foo", "bar")));

        testClient.get()
                .uri("http://localhost:" + port + "/actuator/contentgrid/applications/" + appId)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                            "id": "{APP_ID}",
                            "_links":
                             {
                                 "self": {
                                     "href": "/actuator/contentgrid/applications/{APP_ID}"
                                 },
                                 "config": {
                                     "href": "/actuator/contentgrid/applications/{APP_ID}/config"
                                 },
                                 "client-registration": {
                                     "href": "/actuator/contentgrid/applications/{APP_ID}/client-registration"
                                 }
                             }
                        }
                        """.replaceAll("\\{APP_ID}", appId.toString()));
    }

    @Test
    @WithMockUser
    void getApplicationConfigById() {
        var appId = ApplicationId.random();

        var clientSecret = UUID.randomUUID().toString();

        this.configRepo.merge(ApplicationConfigurationFragment.from(appId, Map.of(
                "foo", "bar",
                Keys.CLIENT_ID, "my-oidc-client",
                Keys.CLIENT_SECRET, clientSecret,
                Keys.ISSUER_URI, "https://auth.contentgrid.com/issuer",
                Keys.ROUTING_DOMAINS, "foo.contentgrid.cloud, bar.contentgrid.cloud",
                Keys.CORS_ORIGINS, "https://frontend.contentgrid.app"
        )));

        testClient.get()
                .uri("http://localhost:" + port + "/actuator/contentgrid/applications/" + appId + "/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                            "applicationId": "{APP_ID}",
                            "clientId": "my-oidc-client",
                            "clientSecret": "************************************",
                            "issuerUri": "https://auth.contentgrid.com/issuer",
                            "domains": [ "foo.contentgrid.cloud", "bar.contentgrid.cloud" ],
                            "corsOrigins": [ "https://frontend.contentgrid.app" ],
                            "_links": {
                                "application": {
                                    "href": "/actuator/contentgrid/applications/{APP_ID}"
                                },
                                "api": [
                                    {
                                        "href": "https://foo.contentgrid.cloud"
                                    },
                                    {
                                        "href": "https://bar.contentgrid.cloud"
                                    }
                                ],
                                "issuer-uri": {
                                    "href": "https://auth.contentgrid.com/issuer"
                                }
                            }
                        }
                        """
                        .replaceAll("\\{APP_ID}", appId.toString())
                );
    }

    @Test
    @WithMockUser
    void getClientRegistration_success() {
        wiremock.stubFor(WireMock.get("/realms/contentgrid/.well-known/openid-configuration")
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withTransformers("response-template")
                        .withBodyFile("well-known-openid-configuration.json")
                ));

        var appId = ApplicationId.random();
        var clientSecret = UUID.randomUUID().toString();

        this.configRepo.merge(ApplicationConfigurationFragment.from(appId, Map.of(
                Keys.CLIENT_ID, "my-oidc-client",
                Keys.CLIENT_SECRET, clientSecret,
                Keys.ISSUER_URI, "http://localhost:"+wiremock.port()+"/realms/contentgrid",
                Keys.ROUTING_DOMAINS, "foo.contentgrid.cloud, bar.contentgrid.cloud",
                Keys.CORS_ORIGINS, "https://frontend.contentgrid.app"
        )));

        testClient.get()
                .uri("http://localhost:" + port + "/actuator/contentgrid/applications/" + appId + "/client-registration")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                            "registration_id": "client-{APP_ID}",
                            "success": true,
                            "client_registration": {
                                "registrationId": "client-{APP_ID}",
                                "clientId": "my-oidc-client",
                                "clientSecret": "************************************"
                            },
                            "_links": {
                                "self": { "href": "/actuator/contentgrid/applications/{APP_ID}/client-registration" },
                                "application": { "href": "/actuator/contentgrid/applications/{APP_ID}" }
                            }
                        }
                        """
                        .replaceAll("\\{APP_ID}", appId.toString())
                );
    }

    @Test
    @WithMockUser
    void getClientRegistration_metadata404() {
        wiremock.stubFor(WireMock.get("/realms/contentgrid/.well-known/openid-configuration")
                .willReturn(WireMock.notFound()));

        var appId = ApplicationId.random();
        var clientSecret = UUID.randomUUID().toString();

        this.configRepo.merge(ApplicationConfigurationFragment.from(appId, Map.of(
                Keys.CLIENT_ID, "my-oidc-client",
                Keys.CLIENT_SECRET, clientSecret,
                Keys.ISSUER_URI, "http://localhost:"+wiremock.port()+"/realms/contentgrid",
                Keys.ROUTING_DOMAINS, "foo.contentgrid.cloud, bar.contentgrid.cloud",
                Keys.CORS_ORIGINS, "https://frontend.contentgrid.app"
        )));

        testClient.get()
                .uri("http://localhost:{port}/actuator/contentgrid/applications/{appId}/client-registration",
                        port, appId)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                            "registration_id": "client-{APP_ID}",
                            "success": false,
                            "error": {
                            
                            },
                            "_links": {
                                "self": { "href": "/actuator/contentgrid/applications/{APP_ID}/client-registration" },
                                "application": { "href": "/actuator/contentgrid/applications/{APP_ID}" }
                            }
                        }
                        """
                        .replaceAll("\\{APP_ID}", appId.toString())
                );

        wiremock.verify(getRequestedFor(WireMock.urlEqualTo("/realms/contentgrid/.well-known/openid-configuration")));
    }
}