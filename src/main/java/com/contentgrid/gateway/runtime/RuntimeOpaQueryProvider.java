package com.contentgrid.gateway.runtime;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_DEPLOY_ID_ATTR;

import com.contentgrid.gateway.runtime.application.ContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import com.contentgrid.thunx.pdp.opa.OpaQueryProvider;
import java.util.Optional;
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

        return Optional.ofNullable(requestContext
                .<DeploymentId>getAttribute(CONTENTGRID_DEPLOY_ID_ATTR))
                .flatMap(serviceCatalog::findByDeploymentId)
                .flatMap(deploymentMetadata::getPolicyPackage)
                .map("data.%s.allow == true"::formatted)
                .orElseGet(() -> {
                    log.warn("No policy found for request to '{}'", requestContext.getRequest().getURI().getHost());
                    return NO_MATCH_QUERY;
                });
    }
}
