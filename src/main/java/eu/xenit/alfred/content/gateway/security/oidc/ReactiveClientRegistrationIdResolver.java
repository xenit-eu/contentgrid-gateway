package eu.xenit.alfred.content.gateway.security.oidc;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface ReactiveClientRegistrationIdResolver {

    Mono<String> resolveRegistrationId(ServerWebExchange exchange);
}
