package com.contentgrid.gateway.runtime;

import static com.contentgrid.gateway.runtime.RuntimeOpaQueryProvider.NO_MATCH_QUERY;
import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_DEPLOY_ID_ATTR;
import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.application.ContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata;
import com.contentgrid.thunx.pdp.RequestContext;
import com.contentgrid.thunx.spring.security.ServerWebExchangeRequestContext;
import java.util.Optional;
import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RuntimeOpaQueryProviderTest {

    protected ServiceCatalog serviceCatalog;
    protected ContentGridDeploymentMetadata deploymentMetadata = new SimpleContentGridDeploymentMetadata();

    @BeforeEach
    void setup() {
        this.serviceCatalog = Mockito.mock(ServiceCatalog.class);
    }

    @Test
    void createQuery() {
        var deployId = DeploymentId.random();
        Mockito.when(serviceCatalog.findByDeploymentId(deployId)).thenReturn(Optional.of(
                ServiceInstanceStubs.serviceInstance(deployId, ApplicationId.random(), "myPolicyPackageName")
        ));
        var queryProvider = new RuntimeOpaQueryProvider(serviceCatalog, deploymentMetadata);
        var requestContext = createRequestContext(deployId);

        var query = queryProvider.createQuery(requestContext);

        assertThat(query).isNotNull().isEqualTo("data.myPolicyPackageName.allow == true");
    }

    @Test
    void noDeploymentId_inRequestAttributes() {
        var deployId = DeploymentId.random();
        Mockito.when(serviceCatalog.findByDeploymentId(deployId)).thenReturn(Optional.of(
                ServiceInstanceStubs.serviceInstance(deployId, ApplicationId.random(), "myPolicyPackageName")
        ));
        var queryProvider = new RuntimeOpaQueryProvider(serviceCatalog, deploymentMetadata);
        var requestContext = createRequestContext(null);

        var query = queryProvider.createQuery(requestContext);

        assertThat(query).isNotNull().isEqualTo(NO_MATCH_QUERY);
    }

    @Test
    void deploymentNotFoundInServiceCatalog() {
        var deployId = DeploymentId.random();
        Mockito.when(serviceCatalog.findByDeploymentId(deployId)).thenReturn(Optional.empty());
        var queryProvider = new RuntimeOpaQueryProvider(serviceCatalog, deploymentMetadata);
        var requestContext = createRequestContext(deployId);

        var query = queryProvider.createQuery(requestContext);

        assertThat(query).isNotNull().isEqualTo(NO_MATCH_QUERY);
    }

    @Test
    void serviceInstanceHasNoPolicyPackage() {
        var deployId = DeploymentId.random();
        Mockito.when(serviceCatalog.findByDeploymentId(deployId)).thenReturn(Optional.of(
                ServiceInstanceStubs.serviceInstance(deployId, ApplicationId.random(), "")
        ));
        var queryProvider = new RuntimeOpaQueryProvider(serviceCatalog, deploymentMetadata);
        var requestContext = createRequestContext(deployId);

        var query = queryProvider.createQuery(requestContext);

        assertThat(query).isNotNull().isEqualTo(NO_MATCH_QUERY);
    }

    @NonNull
    private static RequestContext createRequestContext(DeploymentId deployId) {
        var request = MockServerHttpRequest.get("http://foo.userapps.contentgrid.com").build();
        var exchange = MockServerWebExchange.from(request);
        if (deployId != null) {
            exchange.getAttributes().put(CONTENTGRID_DEPLOY_ID_ATTR, deployId);
        }
        return new ServerWebExchangeRequestContext(exchange);
    }
}