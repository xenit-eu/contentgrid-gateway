package com.contentgrid.gateway.runtime.application;

import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.util.StringUtils;

@Slf4j
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
        var policyPackage = service.getMetadata().get("authz.contentgrid.com/policy-package");
        if (!StringUtils.hasText(policyPackage)) {
            log.warn("Service {} (deployment:{}) has no policy package defined",
                    service.getServiceId(), this.getDeploymentId(service));
            return Optional.empty();
        }
        return Optional.of(policyPackage);
    }
}
