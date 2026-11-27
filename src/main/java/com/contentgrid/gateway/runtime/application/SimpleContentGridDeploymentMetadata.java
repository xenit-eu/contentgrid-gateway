package com.contentgrid.gateway.runtime.application;

import com.contentgrid.configuration.applications.ApplicationId;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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
    /**
     * Not a Kubernetes label: service discovery adds the creation timestamp of the Kubernetes Service under this key.
     */
    public static final String METADATA_CREATION_TIMESTAMP = "k8s_creation_timestamp";

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
            return Optional.empty();
        }
        return Optional.of(policyPackage);
    }

    @Override
    public Optional<Instant> getCreationTimestamp(ServiceInstance service) {
        var timestamp = service.getMetadata().get(METADATA_CREATION_TIMESTAMP);
        if (!StringUtils.hasText(timestamp)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(timestamp));
        } catch (DateTimeParseException ex) {
            log.warn("Invalid creation timestamp '{}' on {}", timestamp, service.getServiceId());
            return Optional.empty();
        }
    }
}
