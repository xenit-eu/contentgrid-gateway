package com.contentgrid.gateway.runtime.application;

import com.contentgrid.gateway.runtime.config.kubernetes.KubernetesLabels;
import java.util.Map;
import java.util.UUID;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;

@UtilityClass
class ServiceInstanceStubs {

    static ServiceInstance serviceInstance(ApplicationId applicationId) {
        return serviceInstance(DeploymentId.random(), applicationId, randomPolicyPackage());
    }

    static ServiceInstance serviceInstance(
            @NonNull DeploymentId deploymentId,
            @NonNull ApplicationId applicationId,
            @NonNull String policyPackage) {
        var serviceName = "api-d-%s".formatted(deploymentId.toString());
        var host = "%s.default.svc.cluster.local".formatted(serviceName);
        return new DefaultServiceInstance(
                UUID.randomUUID().toString(),
                serviceName,
                host,
                8080,
                false,
                Map.of(
                        KubernetesLabels.CONTENTGRID_SERVICETYPE, "api",
                        KubernetesLabels.CONTENTGRID_APPID, applicationId.toString(),
                        KubernetesLabels.CONTENTGRID_DEPLOYID, deploymentId.toString(),
                        KubernetesLabels.CONTENTGRID_POLICYPACKAGE, policyPackage
                )
        );
    }

    @NonNull
    static String randomPolicyPackage() {
        return "contentgrid.userapps.x" + UUID.randomUUID().toString().replaceAll("-", "");
    }
}
