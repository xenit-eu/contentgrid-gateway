package com.contentgrid.gateway.security.refresh;

import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface AuthenticationRefresher {
    Mono<Authentication> refresh(Authentication authentication, ServerWebExchange exchange);
}
