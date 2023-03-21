package com.contentgrid.gateway.runtime.routing;

import static com.contentgrid.gateway.test.assertj.MonoAssert.assertThat;

import com.contentgrid.gateway.runtime.ServiceInstanceStubs;
import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.application.ContentGridApplicationMetadata;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import com.contentgrid.gateway.runtime.application.SimpleContentGridApplicationMetadata;
import com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration.Keys;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationFragment;
import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import java.util.Map;
import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class SimpleRuntimeRequestRouterTest {

    protected ContentGridApplicationMetadata applicationMetadata;
    protected ComposableApplicationConfigurationRepository appConfigRepo;
    protected ServiceCatalog serviceCatalog;
    protected RuntimeRequestRouter requestRouter;

    @BeforeEach
    void setup() {
        var deployMetadata = new SimpleContentGridDeploymentMetadata();
        this.applicationMetadata = new SimpleContentGridApplicationMetadata(deployMetadata);
        this.appConfigRepo = new ComposableApplicationConfigurationRepository();
        this.serviceCatalog = new ServiceCatalog(event -> { }, deployMetadata, appConfigRepo);
        this.requestRouter = new SimpleRuntimeRequestRouter(this.serviceCatalog, deployMetadata, appConfigRepo);
    }

    @Test
    void test() {
        var deployId = DeploymentId.random();
        var appId = ApplicationId.random();

        var exchange1 = createExchange("https://{appId}.userapps.contentgrid.com/me", appId);
        assertThat(this.requestRouter.route(exchange1)).isEmptyMono();

        this.appConfigRepo.merge(new ApplicationConfigurationFragment("config-id", appId, Map.of(
                Keys.ROUTING_DOMAINS, "%s.userapps.contentgrid.com".formatted(appId)
        )));
        this.serviceCatalog.handleServiceAdded(ServiceInstanceStubs.serviceInstance(deployId, appId));

        var exchange2 = createExchange("https://{appId}.userapps.contentgrid.com/me", appId);
        assertThat(this.requestRouter.route(exchange2)).hasValue();
    }

    @NonNull
    private static MockServerWebExchange createExchange(String uriTemplate, Object... uriVars) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(uriTemplate, uriVars).build());
    }
}
