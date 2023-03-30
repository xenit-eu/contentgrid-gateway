package com.contentgrid.gateway.runtime.application;

import java.util.Optional;
import org.springframework.cloud.client.ServiceInstance;

public interface ContentGridDeploymentMetadata {

    Optional<ApplicationId> getApplicationId(ServiceInstance service);

    Optional<DeploymentId> getDeploymentId(ServiceInstance service);

    Optional<String> getPolicyPackage(ServiceInstance service);
}
