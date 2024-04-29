package com.contentgrid.gateway.security.refresh;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Slf4j
public class AuthenticationRefreshingServerSecurityContextRepository implements ServerSecurityContextRepository {
    @NonNull
    private final ServerSecurityContextRepository delegate;

    @NonNull
    private final AuthenticationRefresher authenticationRefresher;

    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        return delegate.save(exchange, context);
    }

    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        return delegate.load(exchange)
                .flatMap(context -> authenticationRefresher.refresh(context.getAuthentication(), exchange)
                        .flatMap(authentication -> {
                            // Update authentication and context when it has changed
                            if(authentication != context.getAuthentication()) {
                                context.setAuthentication(authentication);
                                return save(exchange, context)
                                        .thenReturn(context);
                            }
                            return Mono.just(context);
                        })
                        .onErrorResume(AuthenticationException.class, authError -> {
                            log.debug("Authentication error during token refresh. Session was invalidated.", authError);
                            context.setAuthentication(null);
                            return save(exchange, context)
                                    .thenReturn(context);
                        })
                );
    }

}
