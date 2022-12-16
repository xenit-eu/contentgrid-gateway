package eu.xenit.alfred.content.gateway.routing;

import eu.xenit.alfred.content.gateway.servicediscovery.AppService;
import eu.xenit.alfred.content.gateway.servicediscovery.ServiceAddedHandler;
import eu.xenit.alfred.content.gateway.servicediscovery.ServiceDeletedHandler;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.util.Loggers;

@Slf4j
@RequiredArgsConstructor
public class ServiceTracker implements ServiceAddedHandler, ServiceDeletedHandler, RouteDefinitionLocator {
    private final ApplicationEventPublisher publisher;
    private final RouteLocatorBuilder routeLocatorBuilder;

    private final Map<String, AppService> services = new ConcurrentHashMap<>();

    private RouteDefinition createRouteDefinition(AppService appService) {
        var routeDef = new RouteDefinition();
        routeDef.setId("k8s-"+appService.id());
        routeDef.setUri(URI.create(appService.serviceUrl()));

        var hostnamePredicate = new PredicateDefinition();

        hostnamePredicate.setName("Host");
        hostnamePredicate.addArg("patterns", appService.hostname());
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
    public void handleServiceAdded(AppService service) {
        services.put(service.id(), service);
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }

    @Override
    public void handleServiceDeleted(AppService service) {
        services.remove(service.id());
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }

    public Stream<AppService> findServices(Predicate<AppService> predicate) {
        return services.values().stream().filter(predicate);
    }

}
