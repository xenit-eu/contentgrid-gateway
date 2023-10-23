package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.runtime.application.DeploymentId;
import java.util.Optional;
import org.springframework.web.server.ServerWebExchange;

public interface DeploymentIdRequestResolver {

    Optional<DeploymentId> resolveDeploymentId(ServerWebExchange exchange);

}
