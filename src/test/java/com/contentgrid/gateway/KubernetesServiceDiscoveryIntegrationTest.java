package com.contentgrid.gateway;


import static io.fabric8.kubernetes.client.Config.fromKubeconfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.thunx.pdp.RequestContext;
import com.contentgrid.thunx.pdp.opa.OpaQueryProvider;
import com.contentgrid.thunx.spring.security.ServerWebExchangeRequestContext;
import com.dajudge.kindcontainer.KindContainer;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
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
            "contentgrid.gateway.runtime-platform.enabled=true",
            "spring.main.cloud-platform=kubernetes",
            "servicediscovery.namespace=default",
            "servicediscovery.enabled=true",
    })
    public class HappyPathTest {
        @Autowired
        RouteLocator routeLocator;

        @Autowired
        OpaQueryProvider opaQueryProvider;

        @Test
        public void testConfiguredServiceDiscoveryHappyPath() {
            var appId = ApplicationId.random();
            var deploymentId = DeploymentId.random();

            List<Route> routes = routeLocator.getRoutes().collectList().block();
            assertThat(routes).isNotNull();
            assertThat(routes).isEmpty();

            var request = MockServerHttpRequest.get("https://{appId}.userapps.contentgrid.com", appId).build();
            var exchange = MockServerWebExchange.from(request);
            var requestContext = new ServerWebExchangeRequestContext(exchange);

            Service service = new ServiceBuilder()
                    .withNewMetadata()
                        .withName("integration-test-dummy-service")
                        .withLabels(Map.of(
                                "app.kubernetes.io/managed-by", "contentgrid",
                                "app.contentgrid.com/service-type", "api",
                                "app.contentgrid.com/application-id", appId.toString(),
                                "app.contentgrid.com/deployment-id", deploymentId.toString(),
                                "authz.contentgrid.com/policy-package", "contentgrid.userapps.deployment%s"
                                        .formatted(deploymentId.toString().replace("-", ""))
                        ))
                    .endMetadata()
                    .withNewSpec()
                        .withPorts(new ServicePortBuilder().withPort(8080).withName("http").build())
                        .withSelector(Map.of("app.contentgrid.com/deployment-id", deploymentId.toString()))
                    .endSpec()
                    .build();
            try (KubernetesClient client = new KubernetesClientBuilder().withConfig(fromKubeconfig(K8S.getKubeconfig())).build()) {
                client.resource(service).inNamespace("default").createOrReplace();
            }

            await()
                    .atMost(30, TimeUnit.SECONDS)
                    .pollInterval(1, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        assertThat(routeLocator.getRoutes().collectList().block()).hasSize(1);
                        assertThat(opaQueryProvider.createQuery(requestContext))
                                .isEqualTo("data.contentgrid.userapps.deployment%s.allow == true"
                                        .formatted(deploymentId.toString().replace("-", "")));
                    });

            var newRoutes = routeLocator.getRoutes().collectList().block();

            assertThat(newRoutes.get(0).getFilters()).isNotEmpty();
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
            assertThat(routes).isNotNull();
            assertThat(routes.get(0).getUri().toString()).isEqualTo("http://example.com:80");
        }
    }

}
