package com.contentgrid.gateway.runtime.application;

import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.util.StringUtils;

@Slf4j
public class SimpleContentGridDeploymentMetadata implements ContentGridDeploymentMetadata {

    public static final String LABEL_APPLICATION_ID = "app.contentgrid.com/application-id";
    public static final String LABEL_DEPLOYMENT_ID = "app.contentgrid.com/deployment-id";
    public static final String ANNOTATION_POLICY_PACKAGE = "authz.contentgrid.com/policy-package";

    public Optional<ApplicationId> getApplicationId(@NonNull ServiceInstance service) {
        return Optional.ofNullable(service.getMetadata().get(LABEL_APPLICATION_ID))
                .map(ApplicationId::from);
    }

    @Override
    public Optional<DeploymentId> getDeploymentId(ServiceInstance service) {
        return Optional.ofNullable(service.getMetadata().get(LABEL_DEPLOYMENT_ID))
                .map(DeploymentId::from);
    }

    @Override
    public Optional<String> getPolicyPackage(ServiceInstance service) {
        var policyPackage = service.getMetadata().get(ANNOTATION_POLICY_PACKAGE);
        if (!StringUtils.hasText(policyPackage)) {
            log.warn("Service {} (deployment:{}) has no policy package defined",
                    service.getServiceId(), this.getDeploymentId(service));
            return Optional.empty();
        }
        return Optional.of(policyPackage);
    }
}
