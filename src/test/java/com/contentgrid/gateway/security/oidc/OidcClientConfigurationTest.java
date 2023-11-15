package com.contentgrid.gateway.security.oidc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration.Keys;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationFragment;
import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import com.contentgrid.gateway.test.assertj.MonoAssert;
import java.util.Map;
import lombok.NonNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class OidcClientConfigurationTest {

    @Nested
    @SpringBootTest(properties = "contentgrid.gateway.runtime-platform.enabled=true")
    class RuntimePlatformOAuth2ClientConfiguration {

        @Autowired
        ComposableApplicationConfigurationRepository appConfigRepository;

        @Autowired
        ReactiveClientRegistrationRepository clientRegistrationRepository;

        @Autowired
        ReactiveClientRegistrationIdResolver clientIdResolver;

        @MockBean
        ReactiveClientRegistrationResolver clientResolver;


        @Test
        void testClientRegistrationCycle() {
            var appId = ApplicationId.random();
            var clientId = clientIdResolver.resolveRegistrationId(appId);

            // when asked to build a client-registration given app-config, return a stubbed client-registration
            when(clientResolver.buildClientRegistration(argThat(config -> config.getApplicationId().equals(appId))))
                    .thenReturn(clientId.map(OidcClientConfigurationTest::createClientRegistration));

            // lookup client-registration by registration-id, expect empty result
            var clientNotFound = clientId.flatMap(clientRegistrationRepository::findByRegistrationId);
            MonoAssert.assertThat(clientNotFound).isEmptyMono();

            // update the config-repo, subscribed client-registration-service should get reactor update-events
            appConfigRepository.merge(ApplicationConfigurationFragment.from(appId, Map.of(
                    Keys.ROUTING_DOMAINS, "my.domain.test"
            )));

            // now the config-repo has sent update-event to subscribers, client-registration-repo should return client
            var clientFound = clientId.flatMap(clientRegistrationRepository::findByRegistrationId);
            MonoAssert.assertThat(clientFound).hasValue();

            // clear all configs, triggering a clear-event to the client-registration subscriber
            appConfigRepository.clear();
            var clientGone = clientId.flatMap(clientRegistrationRepository::findByRegistrationId);
            MonoAssert.assertThat(clientGone).isEmptyMono();
        }

    }

    @NonNull
    private static ClientRegistration createClientRegistration(String registrationId) {
        return ClientRegistration.withRegistrationId(registrationId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("client-id")
                .redirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}")
                .authorizationUri("https://auth.contentgrid.com/realms/name/protocol/openid-connect/auth")
                .tokenUri("https://auth.contentgrid.com/realms/name/protocol/openid-connect/token")
                .build();
    }

}