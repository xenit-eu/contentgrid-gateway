package com.contentgrid.gateway.runtime.application;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.springframework.cloud.client.ServiceInstance;

public class SimpleContentGridApplicationMetadata implements ContentGridApplicationMetadata {

    private final ContentGridDeploymentMetadata deploymentMetadata;

    public SimpleContentGridApplicationMetadata(ContentGridDeploymentMetadata deploymentMetadata) {
        this.deploymentMetadata = deploymentMetadata;
    }

    @Override
    public Optional<String> getApplicationId(ServiceInstance service) {
        return deploymentMetadata.getApplicationId(service);
    }

    @Override
    @Deprecated
    public Set<String> getDomainNames(@NonNull ServiceInstance service) {
        return this.getApplicationId(service)
                .stream()
                .map("%s.userapps.contentgrid.com"::formatted)
                .collect(Collectors.toSet());
    }
}
