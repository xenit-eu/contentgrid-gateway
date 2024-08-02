package com.contentgrid.gateway.runtime.routing;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_APP_ID_ATTR;

import com.contentgrid.configuration.applications.ApplicationId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.server.ServerWebExchange;

@RequiredArgsConstructor
public class CachingApplicationIdRequestResolver implements ApplicationIdRequestResolver {

    private final ApplicationIdRequestResolver delegate;

    @Override
    public Optional<com.contentgrid.configuration.applications.ApplicationId> resolveApplicationId(
            ServerWebExchange exchange) {
        return this.loadFromWebExchange(exchange)
                .or(() -> this.delegate.resolveApplicationId(exchange)
                        .map(appId -> this.storeInWebExchange(exchange, appId)));
    }

    private Optional<ApplicationId> loadFromWebExchange(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getAttribute(CONTENTGRID_APP_ID_ATTR))
                .filter(ApplicationId.class::isInstance)
                .map(ApplicationId.class::cast);
    }

    private ApplicationId storeInWebExchange(ServerWebExchange exchange, ApplicationId appId) {
        exchange.getAttributes().put(CONTENTGRID_APP_ID_ATTR, appId);
        return appId;
    }
}
