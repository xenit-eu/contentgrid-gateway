package com.contentgrid.gateway.security.oauth2.client.registration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class DynamicReactiveClientRegistrationRepository implements ReactiveClientRegistrationRepository,
        IterableClientRegistrationIds {

    /**
     * Time that a successfully resolved client registration is cached
     */
    private static final Duration CACHE_SUCCESSFUL_DURATION = Duration.ofMinutes(10);
    /**
     * Time that a failure to resolve a client registration is cached
     */
    private static final Duration CACHE_FAILURE_DURATION = Duration.ofMinutes(1);

    private final Map<String, Mono<ClientRegistration>> clientIdToClientRegistration = new ConcurrentHashMap<>();

    public DynamicReactiveClientRegistrationRepository(Flux<ClientRegistrationEvent> events) {
        events.subscribe(this::onClientRegistrationEvent);
    }

    private void onClientRegistrationEvent(ClientRegistrationEvent event) {
        log.debug("onClientRegistrationEvent: {}", event);
        switch (event.getType()) {
            case PUT -> this.clientIdToClientRegistration.put(event.getRegistrationId(), cache(event));
            case DELETE -> this.clientIdToClientRegistration.remove(event.getRegistrationId());
            case CLEAR -> this.clientIdToClientRegistration.clear();
        }
    }

    /**
     * The {@code Mono<ClientRegistration>} gets cached here, so it will be lazily evaluated and cached
     * 1. when called for the first time, it will do some HTTP call to .well-known/openid-configuration
     * 2. subsequent calls will reuse the cached value, until {@link #CACHE_SUCCESSFUL_DURATION}
     *
     * In case of a failure to retrieve the openid-configuration (timeout, server error, ...), the failure gets cached for {@link #CACHE_FAILURE_DURATION}
     */
    private static Mono<ClientRegistration> cache(ClientRegistrationEvent event) {
        return event.getClientRegistration()
                .cache(cr -> CACHE_SUCCESSFUL_DURATION, ex -> CACHE_FAILURE_DURATION, () -> Duration.ZERO)
                .doOnError(ex -> log.error(
                        "ClientRegistration with id '{}' could not be resolved", event.getRegistrationId(), ex)
                )
                // Wrap in a different exception, so:
                // 1. We indicate that this is expected to be a temporary problem
                // 2. A fresh exception is created every time after Mono.cache(), so the suppressed reactor checkpoints exception is not added to the cached exception
                //      Otherwise, reactor keeps appending additional checkpoints to the list all the time, which is a denial-of-service (out of memory) vulnerability
                .onErrorMap(ex -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OAuth client registration is unavailable", ex));
    }

    @Override
    public Mono<ClientRegistration> findByRegistrationId(String registrationId) {

        return this.clientIdToClientRegistration.getOrDefault(registrationId, Mono.empty())
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("ClientRegistration with id '{}' not found", registrationId);
                    return Mono.empty();
                }))
                .doOnNext(clientRegistration -> log.debug("findByRegistrationId({}) -> clientId:{} issuer-uri:{}",
                        registrationId, clientRegistration.getClientId(),
                        clientRegistration.getProviderDetails().getIssuerUri())
                );
    }

    /**
     * Returns an {@code Stream} of {@code String}, representing a {@link ClientRegistration} id.
     * @return an {@code Stream<String>}
     */
    @Override
    public Stream<String> registrationIds() {
        return this.clientIdToClientRegistration.keySet().stream();
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

        public static ClientRegistrationEvent put(@NonNull String registrationId,
                @NonNull Mono<ClientRegistration> clientRegistration) {
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