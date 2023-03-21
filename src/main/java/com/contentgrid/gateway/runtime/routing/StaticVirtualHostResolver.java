package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;

public class StaticVirtualHostResolver implements RuntimeVirtualHostResolver {

    private final Map<String, ApplicationId> mapping = new HashMap<>();

    public StaticVirtualHostResolver(@NonNull Map<String, ApplicationId> mapping) {
        this.mapping.putAll(mapping);
    }

    @Override
    public Optional<ApplicationId> resolve(URI requestURI) {
        return Optional.ofNullable(this.mapping.get(requestURI.getHost()));
    }
}
