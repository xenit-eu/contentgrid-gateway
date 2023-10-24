package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class DefaultRuntimeRequestRouter implements RuntimeRequestRouter {

    private final ServiceCatalog serviceCatalog;
    private final ApplicationIdRequestResolver applicationIdResolver;
    private final RuntimeServiceInstanceSelector serviceInstanceSelector;

    @Override
    public Mono<ServiceInstance> route(ServerWebExchange exchange) {
        return Mono.justOrEmpty(this.applicationIdResolver.resolveApplicationId(exchange))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Could not resolve Host:'{}' to app-id",
                            exchange.getRequest().getURI().getHost());
                    return Mono.empty();
                }))
                .map(this.serviceCatalog::findByApplicationId)
                .map(services -> this.serviceInstanceSelector.selectService(exchange, services))
                .doOnNext(result -> result.ifPresentOrElse(
                        service -> log.debug("Routing '{}' to {}",
                                exchange.getRequest().getURI().getHost(), service.getServiceId()),
                        () -> log.debug("No service found to route request {}",
                                exchange.getRequest().getURI().getHost()))
                )
                .flatMap(Mono::justOrEmpty);


    }
}
