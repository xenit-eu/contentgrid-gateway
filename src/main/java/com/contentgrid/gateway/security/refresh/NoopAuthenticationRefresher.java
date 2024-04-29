package com.contentgrid.gateway.security.refresh;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class NoopAuthenticationRefresher implements AuthenticationRefresher{

    @NonNull
    private final Class<? extends Authentication> authenticationClass;

    @Override
    public Mono<Authentication> refresh(Authentication authentication, ServerWebExchange exchange) {
        if(authenticationClass.isInstance(authentication)) {
            return Mono.just(authentication);
        }
        return Mono.empty();
    }
}
