package com.contentgrid.gateway.runtime.routing;

import static com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata.LABEL_APPLICATION_ID;
import static com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata.LABEL_DEPLOYMENT_ID;
import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_SERVICE_INSTANCE_ATTR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class RuntimeDeploymentGatewayFilterTest {

    @Mock
    private GatewayFilterChain chain;
    private final RuntimeDeploymentGatewayFilter filter = new RuntimeDeploymentGatewayFilter();

    @Test
    void shouldNotFilter_whenGatewayRequestUrl_isMissing() {
        var exchange = exchange("/test", Map.of());
        filter.filter(exchange, chain);

        verify(chain).filter(exchange);
        verifyNoMoreInteractions(chain);
    }

    @Test
    void shouldNotFilter_whenGatewayRequestUrl_schemeIsNotCg() {
        var exchange = exchange("/test", Map.of(
                // this is a valid URI, but this filter should only apply to cg:// prefix
                GATEWAY_REQUEST_URL_ATTR, URI.create("http://app.service.k8s.local/test")
        ));

        filter.filter(exchange, chain);

        verify(chain).filter(exchange);
        verifyNoMoreInteractions(chain);
    }

    @Test
    void shouldThrowNotFoundException_whenNoServiceInstanceIsFound() {
        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> {
            var exchange = exchange("/test", Map.of(
                    GATEWAY_REQUEST_URL_ATTR, URI.create("cg://ignored/test")
            ));

            filter.filter(exchange, chain);
        });
    }

    @Test
    void happyPath() {
        var exchange = exchange("/test", Map.of(
                GATEWAY_REQUEST_URL_ATTR, URI.create("cg://ignored/test"),
                CONTENTGRID_SERVICE_INSTANCE_ATTR, new DefaultServiceInstance(
                        "instance-%s".formatted(UUID.randomUUID()),
                        "service-%s".formatted(this.hashCode()),
                        "app.running.on.k8s.local",
                        8080,
                        false,
                        Map.of(
                                LABEL_APPLICATION_ID, ApplicationId.random().toString(),
                                LABEL_DEPLOYMENT_ID, DeploymentId.random().toString())
                )
        ));

        runFilter(exchange);

        // after the filter was applied, the exchange should be updated ?!
        assertThat(exchange.<URI>getAttribute(GATEWAY_REQUEST_URL_ATTR))
                .isNotNull()
                .hasToString("http://app.running.on.k8s.local:8080/test");
    }

    static ServerWebExchange exchange(String uri, Map<String, Object> attributes) {
        var request = MockServerHttpRequest.method(HttpMethod.GET, uri).build();
        var exchange = MockServerWebExchange.from(request);
        attributes.forEach((key, value) -> exchange.getAttributes().put(key, value));

        return exchange;
    }

    private ServerWebExchange runFilter(ServerWebExchange exchange) {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        var filter = new RuntimeDeploymentGatewayFilter();
        filter.filter(exchange, chain).block();

        return captor.getValue();
    }
}