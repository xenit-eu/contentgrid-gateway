package com.contentgrid.gateway.runtime.application;

import java.util.Optional;
import lombok.NonNull;
import org.springframework.cloud.client.ServiceInstance;

public class SimpleContentGridDeploymentMetadata implements ContentGridDeploymentMetadata {

    public Optional<String> getApplicationId(@NonNull ServiceInstance service) {
        return Optional.ofNullable(service.getMetadata().get("app.contentgrid.com/application-id"));
    }

    @Override
    public Optional<String> getDeploymentId(ServiceInstance service) {
        return Optional.ofNullable(service.getMetadata().get("app.contentgrid.com/deployment-id"));
    }

    @Override
    public Optional<String> getPolicyPackage(ServiceInstance service) {
        return Optional.ofNullable(service.getMetadata().get("authz.contentgrid.com/policy-package"));
    }
}
