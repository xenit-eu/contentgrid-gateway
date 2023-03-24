package com.contentgrid.gateway.runtime.application;

import java.util.Optional;
import lombok.NonNull;
import org.springframework.cloud.client.ServiceInstance;

public class SimpleContentGridDeploymentMetadata implements ContentGridDeploymentMetadata {

    public Optional<ApplicationId> getApplicationId(@NonNull ServiceInstance service) {
        return Optional.ofNullable(service.getMetadata().get("app.contentgrid.com/application-id"))
                .map(ApplicationId::from);
    }

    @Override
    public Optional<DeploymentId> getDeploymentId(ServiceInstance service) {
        return Optional.ofNullable(service.getMetadata().get("app.contentgrid.com/deployment-id"))
                .map(DeploymentId::from);
    }

    @Override
    public Optional<String> getPolicyPackage(ServiceInstance service) {
        return Optional.ofNullable(service.getMetadata().get("authz.contentgrid.com/policy-package"));
    }
}
