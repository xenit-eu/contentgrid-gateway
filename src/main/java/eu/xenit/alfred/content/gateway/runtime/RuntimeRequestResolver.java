package eu.xenit.alfred.content.gateway.runtime;

import java.util.Optional;
import org.springframework.web.server.ServerWebExchange;

public interface RuntimeRequestResolver {

    Optional<String> resolveApplicationId(ServerWebExchange exchange);

    Optional<String> resolveDeploymentId(ServerWebExchange exchange);
}
