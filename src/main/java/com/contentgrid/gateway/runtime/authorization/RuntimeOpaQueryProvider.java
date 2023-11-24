package com.contentgrid.gateway.runtime.authorization;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_DEPLOY_ID_ATTR;

import com.contentgrid.gateway.runtime.application.ContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import com.contentgrid.thunx.pdp.opa.OpaQueryProvider;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
@RequiredArgsConstructor
public class RuntimeOpaQueryProvider implements OpaQueryProvider<ServerWebExchange> {

    private final ServiceCatalog serviceCatalog;
    private final ContentGridDeploymentMetadata deploymentMetadata;

    final static String NO_MATCH_QUERY = "0 == 1";

    @Override
    public String createQuery(ServerWebExchange requestContext) {

        return RuntimeOpaQueryProvider.getDeploymentIdFromExchange(requestContext)
                .flatMap(serviceCatalog::findByDeploymentId)
                .flatMap(deploymentMetadata::getPolicyPackage)
                .map("data.%s.allow == true"::formatted)
                .orElseGet(() -> {
                    log.warn("No policy found for request to '{}'", requestContext.getRequest().getURI().getHost());
                    return NO_MATCH_QUERY;
                });
    }

    // Eventually this should use RequestDeploymentIdResolver, but this will return a Mono<DeploymentId>
    // OpaQueryProvider should be converted into an async API before we can do that
    @NonNull
    private static Optional<DeploymentId> getDeploymentIdFromExchange(ServerWebExchange requestContext) {
        return Optional.ofNullable(requestContext.getAttribute(CONTENTGRID_DEPLOY_ID_ATTR));
    }
}
