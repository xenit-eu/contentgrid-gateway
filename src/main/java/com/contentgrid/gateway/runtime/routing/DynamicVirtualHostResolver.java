package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.collections.ConcurrentLookup;
import com.contentgrid.gateway.collections.ConcurrentLookup.Lookup;
import com.contentgrid.gateway.runtime.application.ApplicationId;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class DynamicVirtualHostResolver implements RuntimeVirtualHostResolver {

    private final ConcurrentLookup<ApplicationId, ApplicationDomainRegistration> lookup = new ConcurrentLookup<>(
            ApplicationDomainRegistration::applicationId
    );
    private final Lookup<String, ApplicationDomainRegistration> lookupByDomain;

    public DynamicVirtualHostResolver(Flux<ApplicationDomainNameEvent> events) {
        events.subscribe(this::onApplicationDomainEvent);

        this.lookupByDomain = this.lookup.createMultiLookup(event -> event.domains().stream());
    }

    void onApplicationDomainEvent(ApplicationDomainNameEvent event) {
        switch (event.type()) {
            case CLEAR -> this.lookup.clear();
            case DELETE -> this.lookup.remove(event.applicationId());
            case PUT -> {
                var registration = new ApplicationDomainRegistration(event.applicationId, event.domains);
                this.lookup.put(registration);
            }
        }
    }

    @Override
    public Optional<ApplicationId> resolve(@NonNull URI requestURI) {

        var requestHost = requestURI.getHost();
        if (requestHost == null) {
            log.warn("URI {} does not have have Host information", requestURI);
            return Optional.empty();
        }

        var registrations = this.lookupByDomain.apply(requestHost);
        if (registrations.isEmpty()) {
            log.debug("resolving {} failed", requestHost);
            return Optional.empty();
        }

        if (registrations.size() > 1) {
            var domains =  registrations.stream()
                    .map(ApplicationDomainRegistration::applicationId)
                    .map(ApplicationId::toString)
                    .collect(Collectors.joining(", "));
            log.warn("CONFLICT resolving {} -> [{}]", requestHost, domains);
            return Optional.empty();
        }

        var appId = registrations.iterator().next().applicationId();
        log.debug("resolving {} -> {}", requestHost, appId);
        return Optional.of(appId);
    }

    @Value
    @Accessors(fluent = true)
    private static class ApplicationDomainRegistration {
        @NonNull
        ApplicationId applicationId;
        @NonNull
        Set<String> domains;
    }

    @Value
    @Accessors(fluent = true)
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ApplicationDomainNameEvent {

        public enum EventType {
            PUT, DELETE, CLEAR
        }

        @NonNull EventType type;
        ApplicationId applicationId;
        Set<String> domains;

        public static ApplicationDomainNameEvent put(@NonNull ApplicationId appId, @NonNull Set<String> domains) {
            return new ApplicationDomainNameEvent(EventType.PUT, appId, Set.copyOf(domains));
        }

        public static ApplicationDomainNameEvent put(@NonNull ApplicationId appId, String... domains) {
            return new ApplicationDomainNameEvent(EventType.PUT, appId, Set.of(domains));
        }

        public static ApplicationDomainNameEvent delete(@NonNull ApplicationId appId) {
            return new ApplicationDomainNameEvent(EventType.DELETE, appId, null);
        }

        public static ApplicationDomainNameEvent clear() {
            return new ApplicationDomainNameEvent(EventType.CLEAR, null, null);
        }


    }
}
