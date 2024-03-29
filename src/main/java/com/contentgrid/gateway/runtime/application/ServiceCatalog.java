package com.contentgrid.gateway.runtime.application;

import com.contentgrid.gateway.collections.ConcurrentLookup;
import com.contentgrid.gateway.collections.ConcurrentLookup.Lookup;
import com.contentgrid.gateway.runtime.servicediscovery.ServiceAddedHandler;
import com.contentgrid.gateway.runtime.servicediscovery.ServiceDeletedHandler;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;

@Slf4j
public class ServiceCatalog implements
        ServiceAddedHandler, ServiceDeletedHandler
{
    @NonNull
    private final ContentGridDeploymentMetadata deploymentMetadata;
    @NonNull
    private final ConcurrentLookup<String, ServiceInstance> services;
    @NonNull
    private final Lookup<ApplicationId, ServiceInstance> lookupByApplicationId;
    @NonNull
    private final Lookup<DeploymentId, ServiceInstance> lookupByDeploymentId;

    public ServiceCatalog(@NonNull ContentGridDeploymentMetadata deploymentMetadata) {
        this.deploymentMetadata = deploymentMetadata;

        this.services = new ConcurrentLookup<>(ServiceInstance::getInstanceId);
        this.lookupByApplicationId = this.services.createLookup(
                service -> this.deploymentMetadata.getApplicationId(service).orElse(null));
        this.lookupByDeploymentId = this.services.createLookup(
                service -> this.deploymentMetadata.getDeploymentId(service).orElse(null));

    }

    @Override
    public void handleServiceAdded(ServiceInstance service) {
        services.add(service);
    }

    @Override
    public void handleServiceDeleted(ServiceInstance service) {
        services.remove(service.getInstanceId());
    }

    public Stream<ServiceInstance> services() {
        return services.stream();
    }

    public Collection<ServiceInstance> findByApplicationId(@NonNull ApplicationId applicationId) {
        var services = this.lookupByApplicationId.apply(applicationId);
        if (log.isDebugEnabled()) {
            log.debug("findByApplicationId({}) -> [{}]", applicationId,
                    services.stream().map(ServiceInstance::getServiceId).collect(Collectors.joining(", ")));
        }
        return services;
    }

    public Optional<ServiceInstance> findByDeploymentId(@NonNull DeploymentId deploymentId) {
        var services = this.lookupByDeploymentId.apply(deploymentId);
        if (services.size() > 1) {
            log.warn("DUPLICATE ENTRY for {} {}", DeploymentId.class.getSimpleName(), deploymentId);
        }

        return services.stream().findAny();
    }
}
