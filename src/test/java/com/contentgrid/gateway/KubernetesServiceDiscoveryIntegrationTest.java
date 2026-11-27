package com.contentgrid.gateway;

import static io.fabric8.kubernetes.client.Config.fromKubeconfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.contentgrid.configuration.applications.ApplicationConfiguration.Keys;
import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceBuilder;
import java.util.HashMap;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.netty.resolver.HostsFileEntriesResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Tag("integration")
@Testcontainers
public class KubernetesServiceDiscoveryIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesServiceDiscoveryIntegrationTest.class);

    @Container
    private static final K3sContainer K8S = new K3sContainer(DockerImageName.parse("rancher/k3s:latest"));

    @Container
    public static final GenericContainer<?> NGINX = new GenericContainer<>(DockerImageName.parse("docker.io/nginx"))
            .withCopyFileToContainer(MountableFile.forClasspathResource("fixtures/test.txt"),
                    "/usr/share/nginx/html/test")
            .withExposedPorts(80);

    @TestConfiguration
    public static class KindClientConfiguration {

        @Bean
        @Primary
        KubernetesClient testKubernetesClient() {
            return new KubernetesClientBuilder().withConfig(fromKubeconfig(K8S.getKubeConfigYaml())).build();
        }

        @Bean
        HttpClientCustomizer dnsResolverCustomizer() {
            return httpClient -> httpClient
                    .wiretap(true)
                    .resolver(nameResolverSpec ->
                            nameResolverSpec.hostsFileEntriesResolver((inetHost, resolvedAddressTypes) -> {
                                if (inetHost.endsWith(".default.svc.cluster.local")) {
                                    try {
                                        var containerHostIp = InetAddress.getByName(NGINX.getHost());
                                        logger.info("DNS lookup '{}' (types:{}) -> {}", inetHost,
                                                resolvedAddressTypes, containerHostIp);
                                        return containerHostIp;
                                    } catch (UnknownHostException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                                return HostsFileEntriesResolver.DEFAULT.address(inetHost, resolvedAddressTypes);
                            }));

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
            "servicediscovery.enabled=true"
    })
    @AutoConfigureWebTestClient
    public class HappyPathTest {

        @Autowired
        RouteLocator routeLocator;

        @Autowired
        ApplicationContext context;

        private WebTestClient webTestClient;

        @BeforeEach
        public void setup() {
            this.webTestClient = WebTestClient
                    .bindToApplicationContext(this.context)
                    // add Spring Security test Support
                    .apply(springSecurity())
                    .configureClient()
                    .responseTimeout(Duration.ofHours(1))
                    .build();
        }

        @Test
        public void testConfiguredServiceDiscoveryHappyPath() {
            var appId = ApplicationId.random();
            var deploymentId = DeploymentId.random();

            webTestClient
                    .mutateWith(mockOidcLogin())
                    .get()
                    .uri("https://{appId}.userapps.contentgrid.com/test", appId)
                    .header("Host", "%s.userapps.contentgrid.com".formatted(appId))
                    .exchange()
                    .expectStatus().is4xxClientError();

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
                    // Swapping out the port to match the NGINX port, so we can reroute the request
                    .withPorts(new ServicePortBuilder().withPort(NGINX.getFirstMappedPort()).withName("http").build())
                    .endSpec()
                    .build();

            ConfigMap configMap = new ConfigMapBuilder()
                    .withNewMetadata()
                    .withName("app-" + UUID.randomUUID())
                    .withLabels(Map.of(
                            "app.kubernetes.io/managed-by", "contentgrid",
                            "app.contentgrid.com/service-type", "gateway",
                            "app.contentgrid.com/application-id", appId.toString()
                    ))
                    .endMetadata()
                    .addToData(Map.of(Keys.ROUTING_DOMAINS, "%s.userapps.contentgrid.com".formatted(appId)))
                    .build();

            try (KubernetesClient client = new KubernetesClientBuilder().withConfig(fromKubeconfig(K8S.getKubeConfigYaml()))
                    .build()) {
                client.resource(service).inNamespace("default").create();
                client.resource(endpointSlice(service, true)).inNamespace("default").create();
                client.resource(configMap).inNamespace("default").create();
                logger.info("Created k8s resources");
            }

            await()
                    .atMost(30, TimeUnit.SECONDS)
                    .pollInterval(1, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        webTestClient
                                .mutateWith(mockOidcLogin())
                                .get()
                                .uri("https://{appId}.userapps.contentgrid.com/test", appId)
                                .header("Host", "%s.userapps.contentgrid.com".formatted(appId))
                                .exchange()
                                .expectStatus().is2xxSuccessful()
                                .expectBody(String.class)
                                .value(body -> assertThat(body).isEqualTo("Hello ContentGrid!"));
                    });
        }
    }

    @Nested
    @Import(NoK8sAvailableClient.class)
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
            "spring.cloud.gateway.server.webflux.routes.0.id=example",
            "spring.cloud.gateway.server.webflux.routes.0.uri=http://example.com",
            "spring.cloud.gateway.server.webflux.routes.0.predicates.0=Path=/example/**",
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

    @Nested
    @Import(KindClientConfiguration.class)
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
            "contentgrid.gateway.runtime-platform.enabled=true",
            "spring.main.cloud-platform=kubernetes",
            "servicediscovery.namespace=resynctest",
            "servicediscovery.enabled=true",
            "servicediscovery.resync=1",

            // https://github.com/spring-cloud/spring-cloud-gateway/issues/2909
            "spring.cloud.gateway.server.webflux.default-filters=PreserveHostHeader"
    })
    @AutoConfigureWebTestClient
    public class ResyncTest {

        @Autowired
        ApplicationContext context;

        @Autowired
        ServiceCatalog serviceCatalog;

        @Test
        public void testNoDuplicateServicesAfterResync() {
            var appId = ApplicationId.random();
            var deploymentId = DeploymentId.random();

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
                    .withPorts(new ServicePortBuilder().withPort(NGINX.getFirstMappedPort()).withName("http").build())
                    .endSpec()
                    .build();

            ConfigMap configMap = new ConfigMapBuilder()
                    .withNewMetadata()
                    .withName("app-" + UUID.randomUUID())
                    .withLabels(Map.of(
                            "app.kubernetes.io/managed-by", "contentgrid",
                            "app.contentgrid.com/service-type", "gateway",
                            "app.contentgrid.com/application-id", appId.toString()
                    ))
                    .endMetadata()
                    .addToData(Map.of(Keys.ROUTING_DOMAINS, "%s.userapps.contentgrid.com".formatted(appId)))
                    .build();

            try (KubernetesClient client = new KubernetesClientBuilder().withConfig(fromKubeconfig(K8S.getKubeConfigYaml()))
                    .build()) {
                client.resource(new NamespaceBuilder().withNewMetadata().withName("resynctest").endMetadata().build()).create();
                client.resource(service).inNamespace("resynctest").create();
                client.resource(endpointSlice(service, true)).inNamespace("resynctest").create();
                client.resource(configMap).inNamespace("resynctest").create();
                logger.info("Created k8s resources");
            }

            // Wait until the first service is registerd
            await()
                    .atMost(30, TimeUnit.SECONDS)
                    .pollInterval(1, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        assertThat(serviceCatalog.findByApplicationId(appId)).hasSize(1);
                    });
            // Wait another 5 seconds, which should cause a few resyncs, and check that it doesn't make duplicate
            // services appear
            Assertions.assertThrows(ConditionTimeoutException.class, () ->
                    await()
                            .atMost(Duration.ofSeconds(5))
                            .until(() -> serviceCatalog.findByApplicationId(appId).size() > 1)
            );
        }
    }

    @Nested
    @Import(KindClientConfiguration.class)
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
            "contentgrid.gateway.runtime-platform.enabled=true",
            "spring.main.cloud-platform=kubernetes",
            "servicediscovery.namespace=default",
            "servicediscovery.enabled=true",
            // OPA is configured but deliberately unreachable: a migrated app must skip it, so any OPA
            // call would fail to connect and the request would not be 2xx.
            "opa.service.url=http://opa.invalid:8181"
    })
    @AutoConfigureWebTestClient
    class MigratedAppSkipsOpaTest {

        @Autowired
        ApplicationContext context;

        private WebTestClient webTestClient;

        @BeforeEach
        void setup() {
            this.webTestClient = WebTestClient
                    .bindToApplicationContext(this.context)
                    .apply(springSecurity())
                    .configureClient()
                    .responseTimeout(Duration.ofHours(1))
                    .build();
        }

        @Test
        void serviceWithoutPolicyPackage_skipsOpaAndIsServed() {
            var appId = ApplicationId.random();
            var deploymentId = DeploymentId.random();

            // a Service WITHOUT the authz.contentgrid.com/policy-package label -> migrated to sidecar OPA
            Service service = new ServiceBuilder()
                    .withNewMetadata()
                    .withName("integration-test-migrated-service")
                    .withLabels(Map.of(
                            "app.kubernetes.io/managed-by", "contentgrid",
                            "app.contentgrid.com/service-type", "api",
                            "app.contentgrid.com/application-id", appId.toString(),
                            "app.contentgrid.com/deployment-id", deploymentId.toString()
                    ))
                    .endMetadata()
                    .withNewSpec()
                    .withPorts(new ServicePortBuilder().withPort(NGINX.getFirstMappedPort()).withName("http").build())
                    .endSpec()
                    .build();

            ConfigMap configMap = new ConfigMapBuilder()
                    .withNewMetadata()
                    .withName("app-" + UUID.randomUUID())
                    .withLabels(Map.of(
                            "app.kubernetes.io/managed-by", "contentgrid",
                            "app.contentgrid.com/service-type", "gateway",
                            "app.contentgrid.com/application-id", appId.toString()
                    ))
                    .endMetadata()
                    .addToData(Map.of(Keys.ROUTING_DOMAINS, "%s.userapps.contentgrid.com".formatted(appId)))
                    .build();

            try (KubernetesClient client = new KubernetesClientBuilder().withConfig(fromKubeconfig(K8S.getKubeConfigYaml()))
                    .build()) {
                client.resource(service).inNamespace("default").create();
                client.resource(endpointSlice(service, true)).inNamespace("default").create();
                client.resource(configMap).inNamespace("default").create();
                logger.info("Created k8s resources");
            }

            // Without the skip the gateway would query the (unreachable) OPA with the "0 == 1" deny sentinel
            // and fail; a 2xx proves OPA was skipped for the migrated application.
            await()
                    .atMost(30, TimeUnit.SECONDS)
                    .pollInterval(1, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        webTestClient
                                .mutateWith(mockOidcLogin())
                                .get()
                                .uri("https://{appId}.userapps.contentgrid.com/test", appId)
                                .header("Host", "%s.userapps.contentgrid.com".formatted(appId))
                                .exchange()
                                .expectStatus().is2xxSuccessful()
                                .expectBody(String.class)
                                .value(body -> assertThat(body).isEqualTo("Hello ContentGrid!"));
                    });
        }
    }


    /**
     * Builds an EndpointSlice for a selector-less {@link Service}, like the EndpointSlice controller would for a
     * Service with a selector: it carries the labels of the Service and the {@code kubernetes.io/service-name} label.
     */
    static EndpointSlice endpointSlice(Service service, boolean ready) {
        var labels = new HashMap<>(service.getMetadata().getLabels());
        labels.put("kubernetes.io/service-name", service.getMetadata().getName());
        return new EndpointSliceBuilder()
                .withNewMetadata()
                .withName(service.getMetadata().getName() + "-slice")
                .withLabels(labels)
                .endMetadata()
                .withAddressType("IPv4")
                .withEndpoints(new EndpointBuilder()
                        .withAddresses("10.0.0.1")
                        .withNewConditions().withReady(ready).endConditions()
                        .build())
                .build();
    }

    static Service apiService(String name, ApplicationId appId, DeploymentId deploymentId, Map<String, String> selector) {
        return new ServiceBuilder()
                .withNewMetadata()
                .withName(name)
                .withLabels(Map.of(
                        "app.kubernetes.io/managed-by", "contentgrid",
                        "app.contentgrid.com/service-type", "api",
                        "app.contentgrid.com/application-id", appId.toString(),
                        "app.contentgrid.com/deployment-id", deploymentId.toString()
                ))
                .endMetadata()
                .withNewSpec()
                .withPorts(new ServicePortBuilder().withPort(NGINX.getFirstMappedPort()).withName("http").build())
                .withSelector(selector)
                .endSpec()
                .build();
    }

    @Nested
    @Import(KindClientConfiguration.class)
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
            "contentgrid.gateway.runtime-platform.enabled=true",
            "spring.main.cloud-platform=kubernetes",
            "servicediscovery.namespace=" + ReadinessTest.NAMESPACE,
            "servicediscovery.enabled=true"
    })
    class ReadinessTest {

        static final String NAMESPACE = "readinesstest";

        @Autowired
        ServiceCatalog serviceCatalog;

        private KubernetesClient client;

        @BeforeEach
        void setup() {
            this.client = new KubernetesClientBuilder().withConfig(fromKubeconfig(K8S.getKubeConfigYaml())).build();
            if (client.namespaces().withName(NAMESPACE).get() == null) {
                client.resource(new NamespaceBuilder().withNewMetadata().withName(NAMESPACE).endMetadata().build())
                        .create();
            }
        }

        @AfterEach
        void teardown() {
            this.client.close();
        }

        @Test
        void serviceIsOnlyRoutable_whileItHasReadyEndpoints() {
            var appId = ApplicationId.random();
            var service = apiService("readiness-" + UUID.randomUUID(), appId, DeploymentId.random(), null);

            var created = client.resource(service).inNamespace(NAMESPACE).create();
            assertStaysEmpty(appId);

            client.resource(endpointSlice(service, false)).inNamespace(NAMESPACE).create();
            assertStaysEmpty(appId);

            client.resource(endpointSlice(service, true)).inNamespace(NAMESPACE).forceConflicts().serverSideApply();
            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(serviceCatalog.findByApplicationId(appId)).singleElement().satisfies(instance ->
                            assertThat(instance.getMetadata())
                                    .containsEntry(SimpleContentGridDeploymentMetadata.METADATA_CREATION_TIMESTAMP,
                                            created.getMetadata().getCreationTimestamp())));

            client.resource(endpointSlice(service, false)).inNamespace(NAMESPACE).forceConflicts().serverSideApply();
            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(serviceCatalog.findByApplicationId(appId)).isEmpty());

            client.resource(endpointSlice(service, true)).inNamespace(NAMESPACE).forceConflicts().serverSideApply();
            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(serviceCatalog.findByApplicationId(appId)).hasSize(1));

            client.resource(endpointSlice(service, true)).inNamespace(NAMESPACE).delete();
            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(serviceCatalog.findByApplicationId(appId)).isEmpty());
        }

        @Test
        void serviceWithSelector_becomesRoutableWhenItsPodIsReady() {
            // the EndpointSlice controller copies the Service labels onto the EndpointSlices it manages
            var appId = ApplicationId.random();
            var deploymentId = DeploymentId.random();
            var podLabels = Map.of("app.contentgrid.com/deployment-id", deploymentId.toString());
            var service = apiService("pod-" + UUID.randomUUID(), appId, deploymentId, podLabels);

            client.resource(service).inNamespace(NAMESPACE).create();
            assertStaysEmpty(appId);

            var pod = new PodBuilder()
                    .withNewMetadata().withName("pod-" + deploymentId).withLabels(podLabels).endMetadata()
                    .withNewSpec()
                    .withTerminationGracePeriodSeconds(0L)
                    .withContainers(new ContainerBuilder().withName("pause")
                            .withImage("registry.k8s.io/pause:3.10").build())
                    .endSpec()
                    .build();
            client.resource(pod).inNamespace(NAMESPACE).create();
            await().atMost(2, TimeUnit.MINUTES).untilAsserted(() ->
                    assertThat(serviceCatalog.findByApplicationId(appId)).hasSize(1));

            client.resource(pod).inNamespace(NAMESPACE).withGracePeriod(0).delete();
            await().atMost(1, TimeUnit.MINUTES).untilAsserted(() ->
                    assertThat(serviceCatalog.findByApplicationId(appId)).isEmpty());
        }

        private void assertStaysEmpty(ApplicationId appId) {
            Assertions.assertThrows(ConditionTimeoutException.class, () ->
                    await()
                            .atMost(Duration.ofSeconds(3))
                            .until(() -> !serviceCatalog.findByApplicationId(appId).isEmpty()));
        }
    }
}
