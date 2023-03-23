package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import java.util.Optional;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;
import org.springframework.web.server.ServerWebExchange;

public interface RuntimeRequestResolver {

    Optional<ApplicationId> resolveApplicationId(ServerWebExchange exchange);

    Optional<DeploymentId> resolveDeploymentId(ServerWebExchange exchange);

    default ServerWebExchangeMatcher matcher() {
        return exchange -> resolveApplicationId(exchange)
                .map(appId -> MatchResult.match())
                .orElse(MatchResult.notMatch());
    }

}
