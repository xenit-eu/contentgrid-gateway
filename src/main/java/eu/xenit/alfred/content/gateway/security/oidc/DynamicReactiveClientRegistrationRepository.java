package eu.xenit.alfred.content.gateway.security.oidc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import reactor.core.publisher.Mono;

@Slf4j
public class DynamicReactiveClientRegistrationRepository
        implements ReactiveClientRegistrationRepository, Iterable<ClientRegistration> {

    private final Map<String, ClientRegistration> clientIdToClientRegistration = new HashMap<>();

    /**
     * Constructs an {@code InMemoryReactiveClientRegistrationRepository} using the provided parameters.
     *
     * @param registrations the client registration(s)
     */
    public DynamicReactiveClientRegistrationRepository(Collection<ClientRegistration> registrations) {
        registrations.forEach(this::addClientRegistration);
    }

    @Override
    public Mono<ClientRegistration> findByRegistrationId(String registrationId) {
        return Mono.justOrEmpty(this.clientIdToClientRegistration.get(registrationId));
    }

    /**
     * Returns an {@code Iterator} of {@link ClientRegistration}.
     * @return an {@code Iterator<ClientRegistration>}
     */
    @Override
    public Iterator<ClientRegistration> iterator() {
        return this.clientIdToClientRegistration.values().iterator();
    }

    public boolean addClientRegistration(@NonNull ClientRegistration clientRegistration) {
        var old = this.clientIdToClientRegistration.putIfAbsent(clientRegistration.getRegistrationId(), clientRegistration);
        if (old != null) {
            log.warn("Ignoring duplicate client registration id: %s".formatted(clientRegistration));
            return false;
        }
        return true;
    }

    public boolean removeClientRegistrationById(@NonNull String clientRegistrationId) {
        return this.clientIdToClientRegistration.remove(clientRegistrationId) != null;
    }

}