package eu.xenit.alfred.content.gateway;


import static com.jayway.restassured.RestAssured.given;
import static io.fabric8.kubernetes.client.Config.fromKubeconfig;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.dajudge.kindcontainer.KindContainer;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@Tag("integration")
@Testcontainers
public class KubernetesServiceDiscoveryIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesServiceDiscoveryIntegrationTest.class);

    @Container
    public static final KindContainer<?> K8S = new KindContainer<>();

    @TestConfiguration
    public static class KindClientConfiguration {
        @Bean
        @Primary
        KubernetesClient testKubernetesClient() {
            return new KubernetesClientBuilder().withConfig(fromKubeconfig(K8S.getKubeconfig())).build();
        }
    }

    @TestConfiguration
    public static class NoK8sAvailableClient {
        @Primary
        KubernetesClient testKubernetesClient() {
            return Mockito.mock(KubernetesClient.class, invocation -> {
                throw new RuntimeException("This is a mock and no methods should be called on it");
            });
        }
    }

    @BeforeEach
    void setup() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Nested
    @Import(KindClientConfiguration.class)
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
            "testing.bootstrap.enable=true",
            "testing.bootstrap.users.0.username=alice",
            "testing.bootstrap.users.0.authorities={\"employers\": [\"BE0999999999\"]}",
            // always succeeds, so we can look at the actuators
            "opa.query=true == true",
            "servicediscovery.namespace=default",
            "servicediscovery.enabled=true",
            "management.endpoints.web.exposure.include=*",
            "management.server.port="
    })
    public class HappyPathTest {

        @Value("${local.server.port}")
        private int port;

        @Test
        public void testConfiguredServiceDiscoveryHappyPath() {
            String appId = UUID.randomUUID().toString();
            String deploymentId = UUID.randomUUID().toString();

            RestAssured.port = port;

            String sessionCookie = given()
                    .body("username=alice&password=alice")
                    .contentType(ContentType.URLENC)
                    .post("/login")
                    .then()
                    .extract().cookie("SESSION");

            given()
                    .log().path()
                    .accept(ContentType.JSON)
                    .cookie(sessionCookie)
                    .get("/actuator/gateway/routes")
                    .then()
                    .log().body()
                    .assertThat().statusCode(200)
                    .and()
                    .assertThat().body(".", hasSize(0));

//            Deployment deployment = new DeploymentBuilder()
//                    .withNewMetadata()
//                        .withLabels(Map.of("app.contentgrid.com/deployment-request-id", deploymentId))
//                    .endMetadata()
//                    .withNewSpec()
//                        .withNewTemplate()
//                            .withNewSpec()
//                                .addNewContainer()
//                                    .withName("integration-test-dummy-pod")
//                                    .withImage("nginx")
//                                .endContainer()
//                            .endSpec()
//                        .endTemplate()
//                    .endSpec()
//                    .build();

            Service service = new ServiceBuilder()
                    .withNewMetadata()
                        .withName("integration-test-dummy-service")
                        .withLabels(Map.of(
                                "app.kubernetes.io/managed-by", "contentgrid",
                                "app.contentgrid.com/service-type", "api",
                                "app.contentgrid.com/app-id", appId,
                                "app.contentgrid.com/deployment-request-id", deploymentId
                        ))
                    .endMetadata()
                    .withNewSpec()
                        .withPorts(new ServicePortBuilder().withPort(8080).withName("http").build())
//                        .withSelector(Map.of("app.contentgrid.com/deployment-request-id", deploymentId))
                    .endSpec()
                    .build();
            try (KubernetesClient client = new KubernetesClientBuilder().withConfig(fromKubeconfig(K8S.getKubeconfig())).build()) {
                client.resource(service).inNamespace("default").createOrReplace();
            }


            await()
                    .atMost(30, TimeUnit.SECONDS)
                    .pollInterval(1, TimeUnit.SECONDS)
                    .untilAsserted(() ->

            given()
                    .log().path()
                    .accept(ContentType.JSON)
                    .cookie(sessionCookie)
                    .get("/actuator/gateway/routes")
                    .then()
                    .log().body()
                    .assertThat().statusCode(200)
                    .and()
                    .assertThat().body("[0].uri", equalTo("http://integration-test-dummy-service.default.svc.cluster.local:8080"))
            );
        }
    }

    @Nested
    @Import(NoK8sAvailableClient.class)
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
            "testing.bootstrap.enable=true",
            "testing.bootstrap.users.0.username=alice",
            "testing.bootstrap.users.0.authorities={\"employers\": [\"BE0999999999\"]}",
            "spring.cloud.gateway.routes.0.id=example",
            "spring.cloud.gateway.routes.0.uri=http://example.com",
            "spring.cloud.gateway.routes.0.predicates.0=Path=/example/**",
            // always succeeds, so we can look at the actuators
            "opa.query=true == true",
            "servicediscovery.namespace=default",
            "servicediscovery.enabled=false",
            "management.endpoints.web.exposure.include=*",
            "management.server.port="
    })
    public class DisabledHappyPathTest {

        @Value("${local.server.port}")
        private int port;

        @Test
        public void testConfiguredServiceDiscoveryHappyPath() {
            RestAssured.port = port;

            String sessionCookie = given()
                    .body("username=alice&password=alice")
                    .contentType(ContentType.URLENC)
                    .post("/login")
                    .then()
                    .extract().cookie("SESSION");

            given()
                    .log().path()
                    .accept(ContentType.JSON)
                    .cookie(sessionCookie)
                    .get("/actuator/gateway/routes")
                    .then()
                    .log().body()
                    .assertThat().statusCode(200)
                    .and()
                    .assertThat().body("[0].uri", equalTo("http://example.com:80"));

        }
    }

}
