package com.contentgrid.gateway.runtime.routing;

import static com.contentgrid.gateway.test.assertj.MonoAssert.assertThat;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.application.ContentGridApplicationMetadata;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import com.contentgrid.gateway.runtime.application.SimpleContentGridApplicationMetadata;
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

class SimpleContentGridRequestRouterTest {

    protected ContentGridApplicationMetadata applicationMetadata;
    protected ServiceCatalog serviceCatalog;

    protected ContentGridRequestRouter requestRouter;

    @BeforeEach
    void setup() {
        var deployMetadata = new SimpleContentGridDeploymentMetadata();
        this.applicationMetadata = new SimpleContentGridApplicationMetadata(deployMetadata);
        this.serviceCatalog = new ServiceCatalog(event -> { }, deployMetadata, applicationMetadata);
        this.requestRouter = new SimpleContentGridRequestRouter(this.serviceCatalog, this.applicationMetadata, deployMetadata);
    }

    @Test
    void test() {
        var deployId = DeploymentId.random();
        var appId = ApplicationId.random();

        var exchange1 = createExchange("https://{appId}.userapps.contentgrid.com/me", appId);
        assertThat(this.requestRouter.route(exchange1)).isEmptyMono();


        var service = serviceInstance(deployId, appId);

        this.serviceCatalog.handleServiceAdded(service);

        var exchange2 = createExchange("https://{appId}.userapps.contentgrid.com/me", appId);
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
