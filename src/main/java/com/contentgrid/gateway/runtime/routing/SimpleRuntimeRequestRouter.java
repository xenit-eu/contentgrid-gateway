package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.runtime.application.ContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
public class SimpleRuntimeRequestRouter implements RuntimeRequestRouter {

    @NonNull
    private final ServiceCatalog serviceCatalog;
    @NonNull
    private final ContentGridDeploymentMetadata deploymentMetadata;
    @NonNull
    private final ApplicationConfigurationRepository appConfigRepo;
    @NonNull
    private final RuntimeServiceInstanceSelector runtimeServiceInstanceSelector;


    public SimpleRuntimeRequestRouter(ServiceCatalog serviceCatalog,
            ContentGridDeploymentMetadata deploymentMetadata,
            ApplicationConfigurationRepository appConfigRepo) {
        this.serviceCatalog = serviceCatalog;
        this.deploymentMetadata = deploymentMetadata;
        this.appConfigRepo = appConfigRepo;
        this.runtimeServiceInstanceSelector = new SimpleRuntimeServiceInstanceSelector(deploymentMetadata);
    }

    @Override
    public Mono<ServiceInstance> route(ServerWebExchange exchange) {
        var candidates = this.findAppServices(exchange);
        var selection = this.runtimeServiceInstanceSelector.selectService(exchange, candidates);
        return Mono.justOrEmpty(selection);
    }

    private Set<ServiceInstance> findAppServices(ServerWebExchange exchange) {
        return serviceCatalog.services().filter(service -> {
            var requestHost = exchange.getRequest().getURI().getHost();
            var domains = this.deploymentMetadata.getApplicationId(service)
                    .map(this.appConfigRepo::getApplicationConfiguration)
                    .map(ApplicationConfiguration::getDomains)
                    .orElse(Set.of());
            return domains.contains(requestHost.toLowerCase(Locale.ROOT));
        }).collect(Collectors.toSet());
    }

}
