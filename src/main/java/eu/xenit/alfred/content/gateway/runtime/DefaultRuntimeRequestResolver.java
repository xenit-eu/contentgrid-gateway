package eu.xenit.alfred.content.gateway.runtime;

import static eu.xenit.alfred.content.gateway.filter.web.ContentGridAppRequestWebFilter.CONTENTGRID_APP_ID_ATTR;
import static eu.xenit.alfred.content.gateway.filter.web.ContentGridAppRequestWebFilter.CONTENTGRID_DEPLOY_ID_ATTR;

import java.util.Optional;
import org.springframework.web.server.ServerWebExchange;

public class DefaultRuntimeRequestResolver implements RuntimeRequestResolver {

    @Override
    public Optional<ApplicationId> resolveApplicationId(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getAttribute(CONTENTGRID_APP_ID_ATTR))
                .map(Object::toString)
                .flatMap(ApplicationId::from);
    }

    @Override
    public Optional<DeploymentId> resolveDeploymentId(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getAttribute(CONTENTGRID_DEPLOY_ID_ATTR))
                .map(Object::toString)
                .flatMap(DeploymentId::from);
    }
}