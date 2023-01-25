package eu.xenit.alfred.content.gateway.security;

import java.net.URI;
import java.util.function.Function;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.ServerRedirectStrategy;
import org.springframework.security.web.server.savedrequest.ServerRequestCache;
import org.springframework.security.web.server.savedrequest.WebSessionServerRequestCache;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class DynamicRedirectServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final Function<ServerWebExchange, Mono<URI>> mapper;

    private final ServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    private final ServerRequestCache requestCache = new WebSessionServerRequestCache();

    public DynamicRedirectServerAuthenticationEntryPoint(Function<ServerWebExchange, Mono<URI>> mapper) {
        Assert.notNull(mapper, "mapper cannot be null");
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return this.requestCache.saveRequest(exchange)
                .then(this.mapper.apply(exchange))
                .flatMap(location -> this.redirectStrategy.sendRedirect(exchange, location));
    }

}