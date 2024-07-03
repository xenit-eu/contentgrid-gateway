package com.contentgrid.gateway.security.jwt.issuer;

import com.contentgrid.gateway.security.authority.AuthenticationDetails;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface JwtClaimsResolver {
    Mono<JWTClaimsSet> resolveAdditionalClaims(ServerWebExchange exchange, AuthenticationDetails authenticationDetails);

    static JwtClaimsResolver empty() {
        return (exchange, authenticationDetails) -> Mono.just(new JWTClaimsSet.Builder().build());
    }

}
