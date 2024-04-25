package com.contentgrid.gateway.security.jwt.issuer;

import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface JwtClaimsResolver {
    Mono<JWTClaimsSet> resolveAdditionalClaims(ServerWebExchange exchange, AuthenticationInformation authenticationInformation);

    static JwtClaimsResolver empty() {
        return (exchange, authenticationInformation) -> Mono.just(new JWTClaimsSet.Builder().build());
    }

}
