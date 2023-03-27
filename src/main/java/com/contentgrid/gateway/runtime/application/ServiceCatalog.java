package com.contentgrid.gateway.runtime.application;

import com.contentgrid.gateway.collections.ConcurrentLookup;
import com.contentgrid.gateway.collections.ConcurrentLookup.Lookup;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.servicediscovery.ServiceAddedHandler;
import com.contentgrid.gateway.runtime.servicediscovery.ServiceDeletedHandler;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.util.Loggers;

@Slf4j
public class ServiceCatalog implements
        ServiceAddedHandler, ServiceDeletedHandler,
        RouteDefinitionLocator /* replace this later with DiscoveryClientRouteDefinitionLocator */ {

    @NonNull
    private final ApplicationEventPublisher publisher;

    @NonNull
    private final ContentGridDeploymentMetadata deploymentMetadata;

    @NonNull
    private final ApplicationConfigurationRepository appConfigRepository;

    private final ConcurrentLookup<String, ServiceInstance> services;
    private final Lookup<ApplicationId, ServiceInstance> lookupByApplicationId;

    private final Lookup<DeploymentId, ServiceInstance> lookupByDeploymentId;



    public ServiceCatalog(@NonNull ApplicationEventPublisher publisher,
            @NonNull ContentGridDeploymentMetadata deploymentMetadata,
            @NonNull ApplicationConfigurationRepository appConfigRepository) {
        this.publisher = publisher;
        this.deploymentMetadata = deploymentMetadata;
        this.appConfigRepository = appConfigRepository;

        this.services = new ConcurrentLookup<>(ServiceInstance::getInstanceId);
        this.lookupByApplicationId = this.services.createLookup(service -> this.deploymentMetadata.getApplicationId(service).orElse(null));
        this.lookupByDeploymentId = this.services.createLookup(service -> this.deploymentMetadata.getDeploymentId(service).orElse(null));

    }

    private RouteDefinition createRouteDefinition(ServiceInstance service) {
        var routeDef = new RouteDefinition();
        routeDef.setId("k8s-" + service.getServiceId());
        routeDef.setUri(service.getUri());

        var hostnamePredicate = new PredicateDefinition();
        var domainNames = this.deploymentMetadata.getApplicationId(service)
                .map(this.appConfigRepository::getApplicationConfiguration)
                .map(ApplicationConfiguration::getDomains)
                .orElse(Set.of());
        hostnamePredicate.setName("Host");
        hostnamePredicate.addArg("patterns", String.join(",", domainNames));

        routeDef.setPredicates(List.of(hostnamePredicate));
        return routeDef;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromStream(() -> this.services().map(this::createRouteDefinition))
                .log(Loggers.getLogger(ServiceCatalog.class), Level.FINE, false);
    }

    @Override
    public void handleServiceAdded(ServiceInstance service) {
        services.add(service);
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }

    @Override
    public void handleServiceDeleted(ServiceInstance service) {
        services.remove(service.getInstanceId());
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }

    public Stream<ServiceInstance> services() {
        return services.stream();
    }

    public Collection<ServiceInstance> findByApplicationId(@NonNull ApplicationId applicationId) {
        return this.lookupByApplicationId.apply(applicationId);
    }

    public Optional<ServiceInstance> findByDeploymentId(@NonNull DeploymentId deploymentId) {
        var services = this.lookupByDeploymentId.apply(deploymentId);
        if (services.size() > 1) {
            log.warn("DUPLICATE ENTRY for {} {}", DeploymentId.class.getSimpleName(), deploymentId);
        }

        return services.stream().findAny();
    }
}
