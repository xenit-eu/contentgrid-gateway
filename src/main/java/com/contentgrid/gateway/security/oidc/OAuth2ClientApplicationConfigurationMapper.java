package com.contentgrid.gateway.security.oidc;

import com.contentgrid.configuration.api.ComposedConfiguration;
import com.contentgrid.configuration.applications.ApplicationConfiguration;
import com.contentgrid.configuration.applications.ApplicationId;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Slf4j
public class OAuth2ClientApplicationConfigurationMapper implements ReactiveClientRegistrationResolver {

    @NonNull
    private final ReactiveClientRegistrationIdResolver clientRegistrationIdResolver;

    public static final List<String> DEFAULT_SCOPES = List.of("openid", "profile", "email");


    @Override
    public Mono<ClientRegistration> buildClientRegistration(
            @NonNull ComposedConfiguration<ApplicationId, ApplicationConfiguration> applicationConfiguration) {

        return this.clientRegistrationIdResolver.resolveRegistrationId(applicationConfiguration.getCompositionKey())
                .map(clientRegistrationId -> applicationConfiguration.getConfiguration()
                        .map(config -> ClientRegistrations
                                .fromIssuerLocation(config.getIssuerUri())
                                .registrationId(clientRegistrationId)
                                .clientId(config.getClientId())
                                .clientSecret(config.getClientSecret())
                                .scope(DEFAULT_SCOPES)
                                .build()
                        )
                )
                .flatMap(Mono::justOrEmpty);
    }
}
