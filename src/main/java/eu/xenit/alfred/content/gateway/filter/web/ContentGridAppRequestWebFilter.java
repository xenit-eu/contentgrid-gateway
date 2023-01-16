package eu.xenit.alfred.content.gateway.filter.web;

import eu.xenit.alfred.content.gateway.routing.ServiceTracker;
import eu.xenit.alfred.content.gateway.servicediscovery.ContentGridApplicationMetadata;
import eu.xenit.alfred.content.gateway.servicediscovery.ContentGridDeploymentMetadata;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class ContentGridAppRequestWebFilter implements WebFilter {

    /**
     * This {@link WebFilter} needs to be registered before the Spring Security WebFilter, so that authentication
     * and authorization infrastructure can use request attributes set by this WebFilter. The Spring Security
     * WebFilter is registered by {@code WebFluxSecurityConfiguration}
     *
     * @
     * @see <a href="https://github.com/spring-projects/spring-boot/issues/33551">Spring Boot issue 33551</a>
     *
     */
    public static final int CONTENTGRID_WEB_FILTER_CHAIN_FILTER_ORDER = -200;

    public static final String CONTENTGRID_SERVICE_INSTANCE_ATTR = "com.contentgrid.gateway/service-instance";
    public static final String CONTENTGRID_APP_ID_ATTR = "com.contentgrid.gateway/application-id";
    public static final String CONTENTGRID_DEPLOY_ID_ATTR = "com.contentgrid.gateway/deployment-id";

    @NonNull
    private final ServiceTracker serviceTracker;

    @NonNull
    private final ContentGridDeploymentMetadata deploymentMetadata;

    @NonNull
    private final ContentGridApplicationMetadata applicationMetadata;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var candidates = this.findAppServices(exchange);
        var selection = this.selectService(exchange, candidates);

        selection.ifPresentOrElse(service -> {
                    var appId = deploymentMetadata.getApplicationId(service);
                    var deployId = deploymentMetadata.getDeploymentId(service);

                    log.info("{} {} -> app-id: {} deploy-id: {}",
                            exchange.getRequest().getMethod().name(), exchange.getRequest().getURI(),
                            appId.orElse(null), deployId.orElse(null));

                    var attributes = exchange.getAttributes();
                    attributes.put(CONTENTGRID_SERVICE_INSTANCE_ATTR, service);
                    appId.ifPresent(value -> attributes.put(CONTENTGRID_APP_ID_ATTR, value));
                    deployId.ifPresent(value -> attributes.put(CONTENTGRID_DEPLOY_ID_ATTR, value));
                },
                () -> log.info("-- {} no cg-app match found", exchange.getRequest().getURI().getHost()));

        return chain.filter(exchange);
    }

    private Optional<ServiceInstance> selectService(ServerWebExchange exchange, Set<ServiceInstance> candidates) {
        if (candidates.size() > 1) {
            log.info("multiple matches for {}", exchange.getRequest().getURI().getHost());
            candidates.forEach(c -> log.info("-- {}", c.getHost()));
        }
        return candidates.stream().findFirst();
    }

    private Set<ServiceInstance> findAppServices(ServerWebExchange exchange) {
        return this.serviceTracker.services().filter(service -> {
            var requestHost = exchange.getRequest().getURI().getHost();

            var domains = applicationMetadata.getDomainNames(service);
            return domains.contains(requestHost.toLowerCase(Locale.ROOT));
        }).collect(Collectors.toSet());
    }

}
