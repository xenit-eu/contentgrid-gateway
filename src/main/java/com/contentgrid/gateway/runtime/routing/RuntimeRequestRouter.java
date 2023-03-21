package com.contentgrid.gateway.runtime.routing;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Strategy interface to select an appropriate {@link ServiceInstance} for a given {@link ServerWebExchange}.
 */
public interface RuntimeRequestRouter {

    Mono<ServiceInstance> route(ServerWebExchange serverWebExchange);

}
