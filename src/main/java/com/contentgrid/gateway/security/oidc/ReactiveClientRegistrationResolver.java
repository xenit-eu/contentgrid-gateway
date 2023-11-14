package com.contentgrid.gateway.security.oidc;

import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import lombok.NonNull;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import reactor.core.publisher.Mono;

public interface ReactiveClientRegistrationResolver {

    Mono<ClientRegistration> buildClientRegistration(@NonNull ApplicationConfiguration applicationConfiguration);
}
