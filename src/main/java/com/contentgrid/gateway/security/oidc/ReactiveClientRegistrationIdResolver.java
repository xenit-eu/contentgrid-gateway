package com.contentgrid.gateway.security.oidc;

import com.contentgrid.gateway.runtime.ApplicationId;
import reactor.core.publisher.Mono;

public interface ReactiveClientRegistrationIdResolver {

    Mono<String> resolveRegistrationId(ApplicationId applicationId);
}
