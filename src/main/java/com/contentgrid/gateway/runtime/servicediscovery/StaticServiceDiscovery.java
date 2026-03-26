package com.contentgrid.gateway.runtime.servicediscovery;

import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;

@RequiredArgsConstructor
public class StaticServiceDiscovery implements ServiceDiscovery{

    @NonNull
    private final StaticServiceDiscoveryProperties properties;
    @NonNull
    private final ServiceAddedHandler serviceAddedHandler;

    @Override
    public void discoverApis() {
        for (var deployment : properties.getDeployments()) {
            serviceAddedHandler.handleServiceAdded(new StaticServiceInstance(deployment));
        }
    }

    @Data
    public static class StaticServiceDiscoveryProperties {
        private List<StaticDeploymentProperties> deployments = new ArrayList<>();
    }

    @Data
    public static class StaticDeploymentProperties {
        private final ApplicationId applicationId;
        private final DeploymentId deploymentId;
        private final String policyPackage;
        private final URI uri;
    }

    @RequiredArgsConstructor
    private static class StaticServiceInstance implements ServiceInstance {
        private final StaticDeploymentProperties deployment;

        @Override
        public String getInstanceId() {
            return getServiceId();
        }

        @Override
        public String getServiceId() {
            return deployment.getDeploymentId().getValue();
        }

        @Override
        public String getHost() {
            return getUri().getHost();
        }

        @Override
        public int getPort() {
            return getUri().getPort();
        }

        @Override
        public boolean isSecure() {
            return getScheme().equals("https");
        }

        @Override
        public String getScheme() {
            return getUri().getScheme();
        }

        @Override
        public URI getUri() {
            return deployment.getUri();
        }

        @Override
        public Map<String, String> getMetadata() {
            var map = new HashMap<String, String>();
            map.put(SimpleContentGridDeploymentMetadata.LABEL_APPLICATION_ID, deployment.getApplicationId().getValue());
            map.put(SimpleContentGridDeploymentMetadata.LABEL_DEPLOYMENT_ID, deployment.getDeploymentId().getValue());
            if(deployment.getPolicyPackage() != null) {
                map.put(SimpleContentGridDeploymentMetadata.ANNOTATION_POLICY_PACKAGE, deployment.getPolicyPackage());
            }
            return Collections.unmodifiableMap(map);
        }
    }
}
