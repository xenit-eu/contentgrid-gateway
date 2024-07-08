package com.contentgrid.gateway.security.oidc;

import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class OAuth2ClientApplicationConfigurationMapper implements ReactiveClientRegistrationResolver {

    @NonNull
    private final ReactiveClientRegistrationIdResolver clientRegistrationIdResolver;

    public static final List<String> DEFAULT_SCOPES = List.of("openid", "profile", "email");


    @Override
    public Mono<ClientRegistration> buildClientRegistration(@NonNull ApplicationConfiguration applicationConfiguration) {

        return this.clientRegistrationIdResolver.resolveRegistrationId(applicationConfiguration.getApplicationId())
                .map(clientRegistrationId -> ClientRegistrations
                        .fromIssuerLocation(applicationConfiguration.getIssuerUri())
                        .registrationId(clientRegistrationId)
                        .clientId(applicationConfiguration.getClientId())
                        .clientSecret(applicationConfiguration.getClientSecret())
                        .scope(DEFAULT_SCOPES)
                        .build());
    }
}
