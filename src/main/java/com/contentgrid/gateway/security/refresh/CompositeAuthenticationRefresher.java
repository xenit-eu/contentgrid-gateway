package com.contentgrid.gateway.security.refresh;

import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CompositeAuthenticationRefresher implements AuthenticationRefresher {
    @NonNull
    private final List<AuthenticationRefresher> refreshers;

    @Override
    public Mono<Authentication> refresh(Authentication authentication, ServerWebExchange exchange) {
        return Flux.concat(refreshers.stream().map(refresher -> refresher.refresh(authentication, exchange)).toList())
                .next();
    }
}
