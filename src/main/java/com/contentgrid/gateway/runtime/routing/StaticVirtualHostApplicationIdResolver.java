package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import org.springframework.web.server.ServerWebExchange;

public class StaticVirtualHostApplicationIdResolver implements ApplicationIdRequestResolver {

    private final Map<String, ApplicationId> mapping = new HashMap<>();

    public StaticVirtualHostApplicationIdResolver(@NonNull Map<String, ApplicationId> mapping) {
        this.mapping.putAll(mapping);
    }

    public Optional<ApplicationId> resolve(URI requestURI) {
        return Optional.ofNullable(this.mapping.get(requestURI.getHost()));
    }

    @Override
    public Optional<ApplicationId> resolveApplicationId(ServerWebExchange exchange) {
        return this.resolve(exchange.getRequest().getURI());
    }
}
