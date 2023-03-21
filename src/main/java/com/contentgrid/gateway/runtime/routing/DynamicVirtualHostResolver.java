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

    private final ConcurrentLookup<ApplicationId, ApplicationDomainNameEvent> lookup = new ConcurrentLookup<>(
            ApplicationDomainNameEvent::applicationId
    );
    private final Lookup<String, ApplicationDomainNameEvent> lookupByDomain;

    public DynamicVirtualHostResolver(Flux<ApplicationDomainNameEvent> events) {
        events.subscribe(this::onApplicationDomainEvent);

        this.lookupByDomain = this.lookup.addMultiIndex(event -> event.domains().stream());
    }

    void onApplicationDomainEvent(ApplicationDomainNameEvent event) {
        switch (event.type()) {
            case CLEAR -> this.lookup.clear();
            case DELETE -> this.lookup.remove(event.applicationId());
            case PUT -> this.lookup.put(event);
        }
    }

    @Override
    public Optional<ApplicationId> resolve(@NonNull URI requestURI) {

        var requestHost = requestURI.getHost();
        if (requestHost == null) {
            log.warn("URI {} does not have have Host information", requestURI);
            return Optional.empty();
        }

        var applications = this.lookupByDomain.apply(requestHost);
        if (applications.isEmpty()) {
            log.debug("resolving {} failed", requestHost);
            return Optional.empty();
        }

        if (applications.size() > 1) {
            var domains =  applications.stream()
                    .map(ApplicationDomainNameEvent::applicationId)
                    .map(ApplicationId::toString)
                    .collect(Collectors.joining(", "));
            log.warn("CONFLICT resolving {} -> [{}]", requestHost, domains);
            return Optional.empty();
        }

        var appId = applications.iterator().next().applicationId();
        log.debug("resolving {} -> {}", requestHost, appId);
        return Optional.ofNullable(appId);
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

        public static ApplicationDomainNameEvent delete(@NonNull ApplicationId appId, @NonNull Set<String> domains) {
            return new ApplicationDomainNameEvent(EventType.DELETE, appId, domains);
        }

        public static ApplicationDomainNameEvent clear() {
            return new ApplicationDomainNameEvent(EventType.CLEAR, null, null);
        }


    }
}
