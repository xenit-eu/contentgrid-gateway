package com.contentgrid.gateway.runtime.authorization;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.isMigratedApplication;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Relays the original user token for an application without a policy package (migrated to a sidecar OPA);
 * otherwise delegates to the token-minting filter. Skipping the per-request token minting is the point of
 * the bypass: a migrated application authorizes itself, so the gateway just forwards the token.
 */
@RequiredArgsConstructor
public class PolicyPackageTokenGatewayFilter implements GatewayFilter {

    @NonNull
    private final GatewayFilter mint;

    @NonNull
    private final GatewayFilter relay;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return (isMigratedApplication(exchange) ? relay : mint).filter(exchange, chain);
    }
}
