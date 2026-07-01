package com.contentgrid.gateway.runtime.authorization;

import com.contentgrid.thunx.spring.security.ReactivePolicyAuthorizationManager;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Relays the original user token when the gateway produced no ABAC predicate — i.e. OPA was skipped for a
 * migrated application that authorizes itself via its sidecar; otherwise delegates to the token-minting
 * filter. Keying off the residual set by the authorization manager keeps this in lockstep with whether
 * OPA actually ran, rather than re-deriving the "migrated" check (the two could disagree).
 */
@RequiredArgsConstructor
public class PolicyPackageTokenGatewayFilter implements GatewayFilter {

    @NonNull
    private final GatewayFilter mint;

    @NonNull
    private final GatewayFilter relay;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var opaSkipped = exchange.getAttribute(ReactivePolicyAuthorizationManager.ABAC_POLICY_PREDICATE_ATTR) == null;
        return (opaSkipped ? relay : mint).filter(exchange, chain);
    }
}
