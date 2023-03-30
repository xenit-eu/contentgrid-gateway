package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.runtime.application.ContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
@RequiredArgsConstructor
public class SimpleRuntimeServiceInstanceSelector implements RuntimeServiceInstanceSelector {

    private final ContentGridDeploymentMetadata serviceMetadata;

    public Optional<ServiceInstance> selectService(ServerWebExchange exchange, Collection<ServiceInstance> candidates) {

        if (candidates.size() > 1) {
            // logging a warning until we have a better service selection
            log.warn("multiple matches {}: {}", exchange.getRequest().getURI().getHost(), candidates.stream()
                    .map(service -> serviceMetadata.getDeploymentId(service).map(DeploymentId::toString)
                            .orElse("<none>")).toList());
        }

        // sorting based on deployment-id alphabetical order, to get at least a stable selection
        return candidates.stream().min((service1, service2) -> {
            var d1 = serviceMetadata.getDeploymentId(service1).map(DeploymentId::toString).orElse("");
            var d2 = serviceMetadata.getDeploymentId(service2).map(DeploymentId::toString).orElse("");
            return d1.compareTo(d2);
        });
    }
}
