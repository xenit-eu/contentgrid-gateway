package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.runtime.application.ContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.server.ServerWebExchange;

/**
 * Selects the oldest deployment of an application.
 * <p>
 * Service discovery only offers deployments that have ready endpoints. When a new deployment is rolled out next to
 * an old one, traffic keeps going to the old deployment until it is removed (or stops being ready), and then moves to
 * the new one. The choice only depends on the candidates, so all gateway replicas make the same choice.
 */
@Slf4j
@RequiredArgsConstructor
public class SimpleRuntimeServiceInstanceSelector implements RuntimeServiceInstanceSelector {

    private final ContentGridDeploymentMetadata serviceMetadata;

    public Optional<ServiceInstance> selectService(ServerWebExchange exchange, Collection<ServiceInstance> candidates) {

        if (candidates.size() > 1) {
            log.debug("multiple matches {}: {}", exchange.getRequest().getURI().getHost(), candidates.stream()
                    .map(service -> serviceMetadata.getDeploymentId(service).map(DeploymentId::toString)
                            .orElse("<none>")).toList());
        }

        return candidates.stream().min(
                Comparator.comparing((ServiceInstance service) -> serviceMetadata.getCreationTimestamp(service)
                                .orElse(Instant.MAX))
                        .thenComparing(service -> serviceMetadata.getDeploymentId(service)
                                .map(DeploymentId::toString).orElse("")));
    }
}
