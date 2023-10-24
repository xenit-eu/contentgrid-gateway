package com.contentgrid.gateway.runtime.routing;

import static com.contentgrid.gateway.test.assertj.MonoAssert.assertThat;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.config.kubernetes.KubernetesLabels;
import java.util.Map;
import java.util.UUID;
import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class DefaultRuntimeRequestRouterTest {

    private static final ApplicationId APP_ID_1 = ApplicationId.random();

    private RuntimeRequestRouter requestRouter;
    private ServiceCatalog serviceCatalog;

    private final ApplicationIdRequestResolver applicationIdResolver = new StaticVirtualHostApplicationIdResolver(
            Map.of("my-app.contentgrid.cloud", APP_ID_1)
    );
    private final SimpleContentGridDeploymentMetadata deployMetadata = new SimpleContentGridDeploymentMetadata();

    @BeforeEach
    void setup() {
        this.serviceCatalog = new ServiceCatalog(deployMetadata);
        RuntimeServiceInstanceSelector serviceInstanceSelector = new SimpleRuntimeServiceInstanceSelector(
                deployMetadata);
        this.requestRouter = new DefaultRuntimeRequestRouter(this.serviceCatalog, this.applicationIdResolver,
                serviceInstanceSelector);
    }

    @Test
    void testSingleBackend() {
        var deployId = DeploymentId.random();

        var exchange1 = createExchange("https://my-app.contentgrid.cloud/me");
        // there are no backends currently, so expecting an empty result
        assertThat(this.requestRouter.route(exchange1)).isEmptyMono();

        // create a backend service and add it to the application catalog
        this.serviceCatalog.handleServiceAdded(serviceInstance(deployId, APP_ID_1));

        var exchange2 = createExchange("https://my-app.contentgrid.cloud/me");
        assertThat(this.requestRouter.route(exchange2)).hasValue();
    }

    @Test
    void testMultipleBackends() {

        var exchange1 = createExchange("https://my-app.contentgrid.cloud/me");
        // there are no backends currently, so expecting an empty result
        assertThat(this.requestRouter.route(exchange1)).isEmptyMono();

        // create multiple backends
        this.serviceCatalog.handleServiceAdded(serviceInstance(DeploymentId.random(), APP_ID_1));
        this.serviceCatalog.handleServiceAdded(serviceInstance(DeploymentId.random(), APP_ID_1));
        this.serviceCatalog.handleServiceAdded(serviceInstance(DeploymentId.random(), APP_ID_1));

        var exchange2 = createExchange("https://my-app.contentgrid.cloud/me");
        assertThat(this.requestRouter.route(exchange2)).hasValue();
    }

    @NonNull
    private static MockServerWebExchange createExchange(String uriTemplate, Object... uriVars) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(uriTemplate, uriVars).build());
    }

    private static ServiceInstance serviceInstance(DeploymentId deploymentId, ApplicationId applicationId) {
        var serviceName = "api-d-%s".formatted(deploymentId.toString());
        var host = "%s.default.svc.cluster.local".formatted(serviceName);
        return new DefaultServiceInstance(
                UUID.randomUUID().toString(),
                serviceName,
                host,
                8080,
                false,
                Map.of(
                        KubernetesLabels.CONTENTGRID_APPID, applicationId.toString(),
                        KubernetesLabels.CONTENTGRID_DEPLOYID, deploymentId.toString(),
                        KubernetesLabels.CONTENTGRID_SERVICETYPE, "api"
                )
        );
    }
}