package com.contentgrid.gateway.security.jwt.issuer;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class LocallyIssuedJwtGatewayFilter implements GatewayFilter {
    private final JwtIssuer issuer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return issuer.issueSubstitutionToken(exchange)
                .map(substitutionToken -> addBearer(exchange, substitutionToken))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    private ServerWebExchange addBearer(ServerWebExchange exchange, OAuth2Token substitutionToken) {
        return exchange.mutate()
                .request(req -> req.headers(headers -> headers.setBearerAuth(substitutionToken.getTokenValue())))
                .build();

    }

}
