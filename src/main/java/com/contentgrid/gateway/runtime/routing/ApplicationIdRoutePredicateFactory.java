package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.runtime.routing.ApplicationIdRoutePredicateFactory.Config;
import java.util.function.Predicate;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.web.server.ServerWebExchange;

public class ApplicationIdRoutePredicateFactory extends AbstractRoutePredicateFactory<Config> {

    private final ApplicationIdRequestResolver applicationIdRequestResolver;

    public ApplicationIdRoutePredicateFactory(ApplicationIdRequestResolver applicationIdRequestResolver) {
        super(Config.class);
        this.applicationIdRequestResolver = applicationIdRequestResolver;
    }

    @Override
    public Predicate<ServerWebExchange> apply(Config config) {
        return new GatewayPredicate() {
            @Override
            public boolean test(ServerWebExchange exchange) {
                return applicationIdRequestResolver.resolveApplicationId(exchange).isPresent();
            }

            @Override
            public Object getConfig() {
                return config;
            }

            @Override
            public String toString() {
                return "ApplicationId";
            }
        };
    }


    public static class Config {


    }
}
