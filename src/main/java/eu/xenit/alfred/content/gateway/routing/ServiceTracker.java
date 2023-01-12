package eu.xenit.alfred.content.gateway.routing;

import eu.xenit.alfred.content.gateway.servicediscovery.ServiceAddedHandler;
import eu.xenit.alfred.content.gateway.servicediscovery.ServiceDeletedHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.util.Loggers;

@Slf4j
@RequiredArgsConstructor
public class ServiceTracker implements
        ServiceAddedHandler, ServiceDeletedHandler,
        RouteDefinitionLocator /* replace this later with DiscoveryClientRouteDefinitionLocator */
        {
    private final ApplicationEventPublisher publisher;
    private final RouteLocatorBuilder routeLocatorBuilder;

    private final Map<String, ServiceInstance> services = new ConcurrentHashMap<>();

    private RouteDefinition createRouteDefinition(ServiceInstance service) {
        var routeDef = new RouteDefinition();
        routeDef.setId("k8s-"+service.getServiceId());
        routeDef.setUri(service.getUri());

        var hostnamePredicate = new PredicateDefinition();

        // TODO fixme - get app-id from request attributes ??
        var appid = service.getMetadata().get("app.contentgrid.com/application-id");
        hostnamePredicate.setName("Host");
        hostnamePredicate.addArg("patterns", "%s.userapps.contentgrid.com".formatted(appid));


        routeDef.setPredicates(List.of(hostnamePredicate));
        return routeDef;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromStream(() -> services.values().stream()
                .map(this::createRouteDefinition)
        ).log(Loggers.getLogger(ServiceTracker.class), Level.FINE, false);
    }

    @Override
    public void handleServiceAdded(ServiceInstance service) {
        services.put(service.getInstanceId(), service);
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }

    @Override
    public void handleServiceDeleted(ServiceInstance service) {
        services.remove(service.getInstanceId());
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }

    public Stream<ServiceInstance> findServices(Predicate<ServiceInstance> predicate) {
        return services.values().stream().filter(predicate);
    }
}
