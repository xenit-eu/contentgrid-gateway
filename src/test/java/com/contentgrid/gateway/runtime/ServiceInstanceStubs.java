package com.contentgrid.gateway.runtime;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.config.kubernetes.KubernetesLabels;
import java.util.Map;
import java.util.UUID;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;

@UtilityClass
public class ServiceInstanceStubs {

    public static ServiceInstance serviceInstance(ApplicationId applicationId) {
        return serviceInstance(DeploymentId.random(), applicationId, randomPolicyPackage());
    }

    public static ServiceInstance serviceInstance(DeploymentId deploymentId, ApplicationId applicationId) {
        return serviceInstance(deploymentId, applicationId, randomPolicyPackage());
    }

    public static ServiceInstance serviceInstance(
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
    public static String randomPolicyPackage() {
        return "contentgrid.userapps.x" + UUID.randomUUID().toString().replaceAll("-", "");
    }
}
