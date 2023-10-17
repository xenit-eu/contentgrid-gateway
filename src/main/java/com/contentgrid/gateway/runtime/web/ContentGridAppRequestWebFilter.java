package com.contentgrid.gateway.runtime.web;

import com.contentgrid.gateway.runtime.application.ContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.routing.RuntimeRequestRouter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.security.reactive.EndpointRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class ContentGridAppRequestWebFilter implements WebFilter {

    /**
     * This {@link WebFilter} needs to be registered before the Spring Security WebFilter, so that authentication and
     * authorization infrastructure can use request attributes set by this WebFilter. The Spring Security WebFilter is
     * registered by {@code WebFluxSecurityConfiguration}.
     *
     * @see <a href="https://github.com/spring-projects/spring-boot/issues/33551">Spring Boot issue 33551</a>
     */
    public static final int CONTENTGRID_WEB_FILTER_CHAIN_FILTER_ORDER = -200;

    public static final String CONTENTGRID_SERVICE_INSTANCE_ATTR = "com.contentgrid.gateway.service-instance";
    public static final String CONTENTGRID_APP_ID_ATTR = "com.contentgrid.gateway.application-id";
    public static final String CONTENTGRID_DEPLOY_ID_ATTR = "com.contentgrid.gateway.deployment-id";

    @NonNull
    private final ContentGridDeploymentMetadata serviceMetadata;

    @NonNull
    private final RuntimeRequestRouter requestRouter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return this.requestRouter.route(exchange)

                // check if this is a request to a gateway actuator
                .switchIfEmpty(EndpointRequest.toAnyEndpoint().matches(exchange)
                        .doOnNext(result -> {
                            // if it is not an actuator request, we might want to log this
                            if (!result.isMatch()) {
                                // potentially return HTTP 503 early here in the future ?
                                var method = exchange.getRequest().getMethod();
                                var uri = exchange.getRequest().getURI();
                                log.warn("No route found: {} {}", method, uri);
                            }
                        })
                        .flatMap(result -> Mono.empty()))
                .doOnNext(service ->
                {
                    var appId = serviceMetadata.getApplicationId(service);
                    var deployId = serviceMetadata.getDeploymentId(service);

                    log.debug("{} {} -> app-id: {} deploy-id: {}",
                            exchange.getRequest().getMethod().name(), exchange.getRequest().getURI(),
                            appId.orElse(null), deployId.orElse(null));

                    var attributes = exchange.getAttributes();
                    attributes.put(CONTENTGRID_SERVICE_INSTANCE_ATTR, service);
                    appId.ifPresent(value -> attributes.put(CONTENTGRID_APP_ID_ATTR, value));
                    deployId.ifPresent(value -> attributes.put(CONTENTGRID_DEPLOY_ID_ATTR, value));
                })
                .then(chain.filter(exchange));
    }

}

