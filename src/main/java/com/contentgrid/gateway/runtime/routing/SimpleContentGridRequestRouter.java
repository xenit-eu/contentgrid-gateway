package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.runtime.application.ContentGridApplicationMetadata;
import com.contentgrid.gateway.runtime.application.ContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
public class SimpleContentGridRequestRouter implements ContentGridRequestRouter {

    private final ServiceCatalog serviceCatalog;
    private final ContentGridApplicationMetadata applicationMetadata;
    private final ContentGridDeploymentMetadata serviceMetadata;

    public SimpleContentGridRequestRouter(ServiceCatalog serviceCatalog,
            ContentGridApplicationMetadata applicationMetadata,
            ContentGridDeploymentMetadata serviceMetadata) {
        this.serviceCatalog = serviceCatalog;
        this.applicationMetadata = applicationMetadata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public Mono<ServiceInstance> route(ServerWebExchange exchange) {
        var candidates = this.findAppServices(exchange);
        var selection = this.selectService(exchange, candidates);
        return Mono.justOrEmpty(selection);
    }

    private Set<ServiceInstance> findAppServices(ServerWebExchange exchange) {
        return serviceCatalog.services().filter(service -> {
            var requestHost = exchange.getRequest().getURI().getHost();

            var domains = applicationMetadata.getDomainNames(service);
            return domains.contains(requestHost.toLowerCase(Locale.ROOT));
        }).collect(Collectors.toSet());
    }

    private Optional<ServiceInstance> selectService(ServerWebExchange exchange, Set<ServiceInstance> candidates) {

        if (candidates.size() > 1) {
            // logging a warning until we have a better service selection
            log.warn("multiple matches {}: {}", exchange.getRequest().getURI().getHost(), candidates.stream()
                    .map(service -> serviceMetadata.getDeploymentId(service).map(DeploymentId::toString).orElse("<none>")).toList());
        }

        // sorting based on deployment-id alphabetical order, to get at least a stable selection
        return candidates.stream().min((service1, service2) -> {
            var d1 = serviceMetadata.getDeploymentId(service1).map(DeploymentId::toString).orElse("");
            var d2 = serviceMetadata.getDeploymentId(service2).map(DeploymentId::toString).orElse("");
            return d1.compareTo(d2);
        });
    }
}
