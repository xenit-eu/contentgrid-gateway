package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.configuration.api.ComposedConfiguration;
import com.contentgrid.configuration.api.lookup.ConcurrentLookup;
import com.contentgrid.configuration.api.lookup.Lookup;
import com.contentgrid.configuration.api.observable.Observable;
import com.contentgrid.configuration.applications.ApplicationConfiguration;
import com.contentgrid.configuration.applications.ApplicationId;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
public class DynamicVirtualHostApplicationIdResolver implements ApplicationIdRequestResolver {

    private final ConcurrentLookup<ApplicationId, ApplicationDomainRegistration> lookup;
    private final Lookup<String, ApplicationDomainRegistration> lookupByDomain;

    public DynamicVirtualHostApplicationIdResolver(
            Observable<ComposedConfiguration<ApplicationId, ApplicationConfiguration>> events) {
        lookup = new ConcurrentLookup<>(ApplicationDomainRegistration::applicationId, () -> events.observe().map(
                event -> event.mapValue(value -> new ApplicationDomainRegistration(value.getCompositionKey(),
                        value.getConfiguration().map(ApplicationConfiguration::getRoutingDomains).orElseGet(Set::of)))
        ));

        this.lookupByDomain = this.lookup.createMultiLookup(event -> event.domains().stream());
    }

    @Override
    public Optional<ApplicationId> resolveApplicationId(ServerWebExchange exchange) {
        var requestURI = exchange.getRequest().getURI();

        var requestHost = requestURI.getHost();
        if (requestHost == null) {
            log.warn("URI {} does not have have Host information", requestURI);
            return Optional.empty();
        }

        var registrations = this.lookupByDomain.get(requestHost);
        if (registrations.isEmpty()) {
            log.debug("No app-domain-registration found for '{}'", requestHost);
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
        log.debug("Resolved {} -> {}", requestHost, appId);
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

}
