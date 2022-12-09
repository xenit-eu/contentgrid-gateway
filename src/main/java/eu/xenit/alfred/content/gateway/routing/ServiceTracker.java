package eu.xenit.alfred.content.gateway.routing;

import eu.xenit.alfred.content.gateway.servicediscovery.AppService;
import eu.xenit.alfred.content.gateway.servicediscovery.ServiceAddedHandler;
import eu.xenit.alfred.content.gateway.servicediscovery.ServiceDeletedHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;

@Slf4j
@RequiredArgsConstructor
public class ServiceTracker implements ServiceAddedHandler, ServiceDeletedHandler, RouteLocator {
    private final ApplicationEventPublisher publisher;
    private final RouteLocatorBuilder routeLocatorBuilder;

    private final Map<String, AppService> services = new ConcurrentHashMap<>();

    public Flux<Route> getRoutes() {
        var builder = routeLocatorBuilder.routes();

        for (AppService service : services.values()) {
            builder.route(service.id(), p -> p
                    .host(service.hostname())
                    .uri(service.serviceUrl())
            );
        }

        if (log.isDebugEnabled()) {
            var routes = builder.build().getRoutes();
            log.debug("Current routes: {}", routes.collectList().block());
            return routes;
        }

        return builder.build().getRoutes();
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
