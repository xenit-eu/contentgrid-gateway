package eu.xenit.alfred.content.gateway;


import static io.fabric8.kubernetes.client.Config.fromKubeconfig;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.dajudge.kindcontainer.KindContainer;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.event.ApplicationEvents;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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

    @Nested
    @Import(KindClientConfiguration.class)
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
            "servicediscovery.namespace=default",
            "servicediscovery.enabled=true",
    })
    public class HappyPathTest {
        @Autowired
        RouteLocator routeLocator;

        @Test
        public void testConfiguredServiceDiscoveryHappyPath() {
            String appId = UUID.randomUUID().toString();
            String deploymentId = UUID.randomUUID().toString();

            List<Route> routes = routeLocator.getRoutes().collectList().block();
            Assertions.assertThat(routes).isNotNull();
            Assertions.assertThat(routes).isEmpty();

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
                    .untilAsserted(() -> Assertions.assertThat(routeLocator.getRoutes().collectList().block()).hasSize(1));

        }
    }

    @Nested
    @Import(NoK8sAvailableClient.class)
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
            "spring.cloud.gateway.routes.0.id=example",
            "spring.cloud.gateway.routes.0.uri=http://example.com",
            "spring.cloud.gateway.routes.0.predicates.0=Path=/example/**",
            "opa.service.url=opa.example.com",
            "servicediscovery.namespace=default",
            "servicediscovery.enabled=false",
    })
    public class DisabledHappyPathTest {

        @Autowired
        private RouteLocator routeLocator;

        @Test
        public void testDisabledServiceDiscoveryHappyPath() {
            List<Route> routes = routeLocator.getRoutes().collectList().block();
            Assertions.assertThat(routes).isNotNull();
            Assertions.assertThat(routes.get(0).getUri().toString()).isEqualTo("http://example.com:80");
        }
    }

}
