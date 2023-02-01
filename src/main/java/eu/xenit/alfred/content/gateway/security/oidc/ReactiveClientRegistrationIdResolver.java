package eu.xenit.alfred.content.gateway.security.oidc;

import eu.xenit.alfred.content.gateway.runtime.ApplicationId;
import reactor.core.publisher.Mono;

public interface ReactiveClientRegistrationIdResolver {

    Mono<String> resolveRegistrationId(ApplicationId applicationId);
}
