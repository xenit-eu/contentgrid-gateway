package com.contentgrid.gateway.security.oidc;

import com.contentgrid.configuration.api.ComposedConfiguration;
import com.contentgrid.configuration.applications.ApplicationConfiguration;
import com.contentgrid.configuration.applications.ApplicationId;
import lombok.NonNull;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import reactor.core.publisher.Mono;

public interface ReactiveClientRegistrationResolver {

    Mono<ClientRegistration> buildClientRegistration(
            @NonNull ComposedConfiguration<ApplicationId, ApplicationConfiguration> applicationConfiguration);
}
