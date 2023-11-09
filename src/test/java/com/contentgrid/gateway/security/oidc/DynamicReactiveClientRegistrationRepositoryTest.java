package com.contentgrid.gateway.security.oidc;

import static com.contentgrid.gateway.test.assertj.MonoAssert.assertThat;

import com.contentgrid.gateway.security.oauth2.client.registration.DynamicReactiveClientRegistrationRepository;
import com.contentgrid.gateway.security.oauth2.client.registration.DynamicReactiveClientRegistrationRepository.ClientRegistrationEvent;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import reactor.core.publisher.Mono;
import reactor.test.publisher.TestPublisher;

@Slf4j
class DynamicReactiveClientRegistrationRepositoryTest {

    private static final ClientRegistration CLIENT1 = clientRegistration("my-client", "realm-abc");
    private static final ClientRegistration CLIENT2 = clientRegistration("other-client", "realm-xyz");

    @Test
    void findByRegistrationId() {
        TestPublisher<ClientRegistrationEvent> publisher = TestPublisher.create();
        var flux = publisher.flux().doOnNext(event -> log.info("event: {}", event));
        var repository = new DynamicReactiveClientRegistrationRepository(flux);

        publisher.next(ClientRegistrationEvent.put(CLIENT1.getRegistrationId(), Mono.just(CLIENT1)));
        publisher.next(ClientRegistrationEvent.put(CLIENT2.getRegistrationId(), Mono.just(CLIENT2)));

        assertThat(repository.findByRegistrationId(CLIENT1.getRegistrationId())).hasValue(CLIENT1);
        assertThat(repository.findByRegistrationId("unknown-registration")).isEmptyMono();

        // clear the repository
        publisher.next(ClientRegistrationEvent.clear());
        assertThat(repository.findByRegistrationId(CLIENT1.getRegistrationId())).isEmptyMono();

    }

    static ClientRegistration clientRegistration(String clientId, String realmName) {
        return ClientRegistration.withRegistrationId("reg-"+clientId)

                // custom values
                .clientId(clientId)
                .clientSecret(UUID.randomUUID().toString())
                .redirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}")

                // from oidc metadata discovery endpoint
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationUri("https://auth.contentgrid.com/realms/%s/protocol/openid-connect/auth".formatted(realmName))
                .tokenUri("https://auth.contentgrid.com/realms/%s/protocol/openid-connect/token".formatted(realmName))
                .issuerUri("https://auth.contentgrid.com/realms/%s".formatted(realmName))
                .clientName("https://auth.contentgrid.com/realms/%s".formatted(realmName))

                .build();
    }



}