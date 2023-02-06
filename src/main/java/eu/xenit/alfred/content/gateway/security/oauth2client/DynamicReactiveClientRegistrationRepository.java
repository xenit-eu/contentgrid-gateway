package eu.xenit.alfred.content.gateway.security.oauth2client;

import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class DynamicReactiveClientRegistrationRepository
        implements ReactiveClientRegistrationRepository {

    private final Map<String, Mono<ClientRegistration>> clientIdToClientRegistration = new HashMap<>();

    public DynamicReactiveClientRegistrationRepository(Flux<ClientRegistrationEvent> events) {
        events.subscribe(this::onClientRegistrationEvent);
    }

    private void onClientRegistrationEvent(ClientRegistrationEvent event) {
        log.debug("onClientRegistrationEvent: {}", event);
        switch (event.getType()) {
            case PUT -> {
                // Note: the Mono<ClientRegistration> gets cached here, so it will be lazily evaluated and cached
                // 1. when called for the first time, it will do some HTTP call to .well-known/openid-configuration
                // 2. all subsequent calls will reuse the cached value, without cache expiration
                this.clientIdToClientRegistration.put(event.getRegistrationId(), event.getClientRegistration().cache());
            }
            case DELETE -> this.clientIdToClientRegistration.remove(event.getRegistrationId());
            case CLEAR -> this.clientIdToClientRegistration.clear();
        }
    }

    @Override
    public Mono<ClientRegistration> findByRegistrationId(String registrationId) {

        return this.clientIdToClientRegistration.get(registrationId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("ClientRegistration with id {} not found", registrationId);
                    return Mono.empty();
                }))
                .doOnNext(clientRegistration -> {
                    log.info("findByRegistrationId({}) -> clientId:{} issuer-uri:{}", registrationId,
                            clientRegistration.getClientId(), clientRegistration.getProviderDetails().getIssuerUri());
                });
    }


    @Value
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ClientRegistrationEvent {

        @NonNull EventType type;
        String registrationId;
        Mono<ClientRegistration> clientRegistration;

        public enum EventType {
            PUT, DELETE, CLEAR
        }

        public static ClientRegistrationEvent put(@NonNull String registrationId, @NonNull Mono<ClientRegistration> clientRegistration) {
            return new ClientRegistrationEvent(EventType.PUT, registrationId, clientRegistration);
        }

        public static ClientRegistrationEvent delete(@NonNull String registrationId) {
            return new ClientRegistrationEvent(EventType.DELETE, registrationId, null);
        }

        public static ClientRegistrationEvent clear() {
            return new ClientRegistrationEvent(EventType.CLEAR, null, null);
        }

        @Override
        public String toString() {
            return "%s:%s".formatted(type.name(), this.getRegistrationId());
        }
    }
}