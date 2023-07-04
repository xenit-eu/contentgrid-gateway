package com.contentgrid.gateway.runtime.session;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.WebSessionManager;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class PartitionedWebSessionManager<P> implements WebSessionManager {

    @NonNull
    private final WebExchangePartitioner<P> partitioner;

    @NonNull
    private final Function<P, WebSessionManager> sessionManagerFactory;

    @NonNull
    private final ConcurrentHashMap<P, WebSessionManager> delegates = new ConcurrentHashMap<>(2);

    @Override
    public Mono<WebSession> getSession(ServerWebExchange exchange) {
        return this.partitioner.apply(exchange)
                .switchIfEmpty(Mono.error(() -> {
                    // bad partition function, did not provide a key for current exchange
                    return new IllegalStateException("Session partition function returned empty value for request");
                }))
                .map(key -> this.delegates.computeIfAbsent(key, this.sessionManagerFactory::apply))
                .flatMap(delegate -> delegate.getSession(exchange));
    }
}
