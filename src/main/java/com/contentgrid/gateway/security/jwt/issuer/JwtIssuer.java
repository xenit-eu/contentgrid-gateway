package com.contentgrid.gateway.security.jwt.issuer;

import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface JwtIssuer {
    JWKSet getJwkSet();
    Mono<OAuth2Token> issueSubstitutionToken(ServerWebExchange exchange);
}
