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
    private final RuntimeVirtualHostResolver virtualHostResolver;
    private final RuntimeServiceInstanceSelector serviceInstanceSelector;

    @Override
    public Mono<ServiceInstance> route(ServerWebExchange exchange) {
        return Mono.just(exchange.getRequest().getURI())
                .map(this.virtualHostResolver::resolve)
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Could not resolve {} to app-id", exchange.getRequest().getURI());
                    return Mono.empty();
                }))
                .map(this.serviceCatalog::findByApplicationId)
                .map(services -> this.serviceInstanceSelector.selectService(exchange, services))
                .doOnNext(result -> result.ifPresentOrElse(
                        service -> log.debug("Routing request {} to {}", exchange.getRequest().getURI(),
                                service.getServiceId()),
                        () -> log.debug("No service found to route request {}", exchange.getRequest().getURI()))
                )
                .flatMap(Mono::justOrEmpty);


    }
}
