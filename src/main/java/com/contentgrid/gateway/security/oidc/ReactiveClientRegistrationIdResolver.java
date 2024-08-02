package com.contentgrid.gateway.security.oidc;

import com.contentgrid.configuration.applications.ApplicationId;
import reactor.core.publisher.Mono;

public interface ReactiveClientRegistrationIdResolver {

    Mono<String> resolveRegistrationId(ApplicationId applicationId);
}
