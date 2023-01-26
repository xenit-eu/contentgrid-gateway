package com.contentgrid.gateway.security.oidc;

import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class OAuth2ClientApplicationConfigurationMapper {

    @NonNull
    private final ReactiveClientRegistrationIdResolver clientRegistrationIdResolver;

    public static final List<String> DEFAULT_SCOPES = List.of("openid", "profile", "email");

    private static class Keys {

        private static final String CLIENT_ID = "contentgrid.idp.client-id";
        private static final String CLIENT_SECRET = "contentgrid.idp.client-secret";
        private static final String ISSUER_URI = "contentgrid.idp.issuer-uri";
    }

    public String getClientId(@NonNull ApplicationConfiguration applicationConfiguration) {
        return applicationConfiguration.getProperty(Keys.CLIENT_ID).orElse(null);
    }

    public String getClientSecret(@NonNull ApplicationConfiguration applicationConfiguration) {
        return applicationConfiguration.getProperty(Keys.CLIENT_SECRET).orElse(null);
    }

    public String getIssuerUri(@NonNull ApplicationConfiguration applicationConfiguration) {
        return applicationConfiguration.getProperty(Keys.ISSUER_URI).orElse(null);
    }

    public Mono<ClientRegistration> getClientRegistration(@NonNull ApplicationConfiguration applicationConfiguration) {

        return this.clientRegistrationIdResolver.resolveRegistrationId(applicationConfiguration.getApplicationId())
                .map(clientRegistrationId -> ClientRegistrations
                        .fromIssuerLocation(this.getIssuerUri(applicationConfiguration))
                        .registrationId(clientRegistrationId)
                        .clientId(this.getClientId(applicationConfiguration))
                        .clientSecret(this.getClientSecret(applicationConfiguration))
                        .scope(DEFAULT_SCOPES)
                        .build());
    }
}
