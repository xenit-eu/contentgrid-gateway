package com.contentgrid.gateway.runtime.authorization;

import com.contentgrid.thunx.spring.security.ReactivePolicyAuthorizationManager;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Mints the JWT for the app server. If the {@code ABAC_POLICY_PREDICATE_ATTR} was set, then the centralised OPA was used
 * and a legacy token is minted.
 * If the predicate isn't set, then the OPA wasn't contacted and the appserver will instead contact its sidecar OPA.
 */
@RequiredArgsConstructor
public class PolicyPackageTokenGatewayFilter implements GatewayFilter {

    @NonNull
    private final GatewayFilter legacyMint;

    @NonNull
    private final GatewayFilter sidecarMint;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var opaSkipped = exchange.getAttribute(ReactivePolicyAuthorizationManager.ABAC_POLICY_PREDICATE_ATTR) == null;
        return (opaSkipped ? sidecarMint : legacyMint).filter(exchange, chain);
    }
}
