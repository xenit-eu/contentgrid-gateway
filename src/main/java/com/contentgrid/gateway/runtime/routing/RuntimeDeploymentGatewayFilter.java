package com.contentgrid.gateway.runtime.routing;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_SERVICE_INSTANCE_ATTR;
import static org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter.LOAD_BALANCER_CLIENT_FILTER_ORDER;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.DelegatingServiceInstance;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class RuntimeDeploymentGatewayFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        URI routedUri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        if (routedUri == null || !"cg".equals(routedUri.getScheme())) {
            return chain.filter(exchange);
        }

        ServiceInstance serviceInstance = exchange.getAttribute(CONTENTGRID_SERVICE_INSTANCE_ATTR);
        if (serviceInstance == null) {
            var host = exchange.getRequest().getURI().getHost();
            var message = "Unable to find service instance for %s".formatted(host);
            throw NotFoundException.create(false /* HTTP 503 */, message);
        } else {

            // the `<scheme>` for routedUri is `cg://`, so we need to override the default scheme
            // if the serviceInstance doesn't provide one.
            String overrideScheme = serviceInstance.isSecure() ? "https" : "http";
            serviceInstance = new DelegatingServiceInstance(serviceInstance, overrideScheme);

            routedUri = LoadBalancerUriTools.reconstructURI(serviceInstance, routedUri);

            if (log.isDebugEnabled()) {
                log.debug("Routing {} to {}", exchange.getRequest().getURI(), serviceInstance.getHost());
            }
            exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, routedUri);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return LOAD_BALANCER_CLIENT_FILTER_ORDER;
    }
}
