package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import java.util.Optional;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;
import org.springframework.web.server.ServerWebExchange;

public interface ApplicationIdRequestResolver {

    Optional<ApplicationId> resolveApplicationId(ServerWebExchange exchange);

    default ServerWebExchangeMatcher matcher() {
        return exchange -> resolveApplicationId(exchange)
                .map(appId -> MatchResult.match())
                .orElse(MatchResult.notMatch());
    }
}
