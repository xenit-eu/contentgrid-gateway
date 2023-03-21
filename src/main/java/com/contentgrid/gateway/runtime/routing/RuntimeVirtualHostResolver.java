package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import java.net.URI;
import java.util.Optional;

public interface RuntimeVirtualHostResolver {

    Optional<ApplicationId> resolve(URI requestURI);

    default Optional<ApplicationId> resolve(String requestUri) {
        return this.resolve(URI.create(requestUri));
    }
}
