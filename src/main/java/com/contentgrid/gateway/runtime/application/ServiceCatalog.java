package com.contentgrid.gateway.runtime.application;

import com.contentgrid.configuration.api.lookup.ConcurrentLookup;
import com.contentgrid.configuration.api.lookup.Lookup;
import com.contentgrid.configuration.applications.ApplicationId;
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
    private final ConcurrentLookup<String, ServiceInstance> servicesLookup;
    @NonNull
    private final Lookup<ApplicationId, ServiceInstance> lookupByApplicationId;
    @NonNull
    private final Lookup<DeploymentId, ServiceInstance> lookupByDeploymentId;

    public ServiceCatalog(@NonNull ContentGridDeploymentMetadata deploymentMetadata) {
        this.deploymentMetadata = deploymentMetadata;

        this.servicesLookup = new ConcurrentLookup<>(ServiceInstance::getInstanceId);
        this.lookupByApplicationId = this.servicesLookup.createLookup(
                service -> this.deploymentMetadata.getApplicationId(service).orElse(null));
        this.lookupByDeploymentId = this.servicesLookup.createLookup(
                service -> this.deploymentMetadata.getDeploymentId(service).orElse(null));

    }

    @Override
    public void handleServiceAdded(ServiceInstance service) {
        servicesLookup.add(service);
    }

    @Override
    public void handleServiceDeleted(ServiceInstance service) {
        servicesLookup.remove(service.getInstanceId());
    }

    public Stream<ServiceInstance> services() {
        return servicesLookup.stream();
    }

    public Collection<ServiceInstance> findByApplicationId(@NonNull ApplicationId applicationId) {
        var services = this.lookupByApplicationId.get(applicationId);
        if (log.isDebugEnabled()) {
            log.debug("findByApplicationId({}) -> [{}]", applicationId,
                    services.stream().map(ServiceInstance::getServiceId).collect(Collectors.joining(", ")));
        }
        return services;
    }

    public Optional<ServiceInstance> findByDeploymentId(@NonNull DeploymentId deploymentId) {
        var services = this.lookupByDeploymentId.get(deploymentId);
        if (services.size() > 1) {
            log.warn("DUPLICATE ENTRY for {} {}", DeploymentId.class.getSimpleName(), deploymentId);
        }

        return services.stream().findAny();
    }
}
