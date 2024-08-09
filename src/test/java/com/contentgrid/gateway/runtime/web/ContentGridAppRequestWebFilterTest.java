package com.contentgrid.gateway.runtime.web;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_APP_ID_ATTR;
import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_DEPLOY_ID_ATTR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.test.runtime.ServiceInstanceStubs;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.routing.RuntimeRequestRouter;
import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ContentGridAppRequestWebFilterTest {

    private SimpleContentGridDeploymentMetadata serviceMetadata = new SimpleContentGridDeploymentMetadata();
    private RuntimeRequestRouter requestRouter;
    private WebFilterChain chain;

    @BeforeEach
    void setup() {
        this.requestRouter = Mockito.mock(RuntimeRequestRouter.class);
        this.chain = Mockito.mock(WebFilterChain.class);

        Mockito.when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    void matchingRoute_hasAttributes() {
        var appId = ApplicationId.random();
        var deploymentId = DeploymentId.random();
        Mockito.when(requestRouter.route(any(ServerWebExchange.class)))
                .thenReturn(Mono.just(ServiceInstanceStubs.serviceInstance(deploymentId, appId)));

        var filter = new ContentGridAppRequestWebFilter(serviceMetadata, requestRouter);

        var request = createExchange(appId);
        var result = filter.filter(request, chain);

        StepVerifier.create(result).verifyComplete();

        assertThat(request.getAttributes()).containsEntry(CONTENTGRID_APP_ID_ATTR, appId);
        assertThat(request.getAttributes()).containsEntry(CONTENTGRID_DEPLOY_ID_ATTR, deploymentId);
    }

    @Test
    void noMatchingRoute_hasNoAttributes() {
        var appId = ApplicationId.random();
        Mockito.when(requestRouter.route(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        var filter = new ContentGridAppRequestWebFilter(serviceMetadata, requestRouter);

        var request = createExchange(appId);
        var result = filter.filter(request, chain);

        StepVerifier.create(result).verifyComplete();

        assertThat(request.getAttributes()).doesNotContainKey(CONTENTGRID_APP_ID_ATTR);
        assertThat(request.getAttributes()).doesNotContainKey(CONTENTGRID_DEPLOY_ID_ATTR);
    }

    @NonNull
    private static MockServerWebExchange createExchange(ApplicationId appId) {
        var uriTemplate = "https://{appId}.userapps.contentgrid.com/me";
        return MockServerWebExchange.from(MockServerHttpRequest.get(uriTemplate, appId).build());
    }
}