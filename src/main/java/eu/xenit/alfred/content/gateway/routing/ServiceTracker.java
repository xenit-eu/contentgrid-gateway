package eu.xenit.alfred.content.gateway.routing;

import com.contentgrid.thunx.pdp.RequestContext;
import eu.xenit.alfred.content.gateway.servicediscovery.AppService;
import eu.xenit.alfred.content.gateway.servicediscovery.ServiceAddedHandler;
import eu.xenit.alfred.content.gateway.servicediscovery.ServiceDeletedHandler;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;

@Slf4j
@RequiredArgsConstructor
public class ServiceTracker implements ServiceAddedHandler, ServiceDeletedHandler, RouteLocator {
    private final static String publicDomainSuffix = ".userapps.contentgrid.com";

    private final ApplicationEventPublisher publisher;
    private final RouteLocatorBuilder routeLocatorBuilder;

    private final Map<String, AppService> services = new ConcurrentHashMap<>();

    public Flux<Route> getRoutes() {
        var builder = routeLocatorBuilder.routes();

        for (AppService service : services.values()) {
            builder.route(service.id(), p -> p
                    .host(service.id() + publicDomainSuffix)
                    .uri(service.getServiceUrl())
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

    public String opaQueryFor(RequestContext request) {
        String hostFromUri = request.getURI().getHost();
        int suffixIndex = hostFromUri.indexOf(publicDomainSuffix);

        if (suffixIndex == -1) {
            log.warn("Request for non-{} host ({}), perhaps the gateway itself, returning tautological opa query",
                    publicDomainSuffix, hostFromUri);
            return "1 == 1";
        }

        String appIdFromUri = hostFromUri.substring(0, suffixIndex);
        Optional<AppService> app = services.values().stream().filter(a -> a.id().equals(appIdFromUri)).findFirst();

        if (app.isEmpty()) {
            log.warn("Received request for {}, but I don't know any apps with id {}", hostFromUri, appIdFromUri);
        } else {
            log.debug("Received request for {}, matched to app {}", hostFromUri, app.get());
        }

        return app.map(a -> "data.contentgrid.userapps.%s.allow == true".formatted(a.getSafeDeploymentId()))
                .orElse("false"); // deny, I guess...?
    }
}
