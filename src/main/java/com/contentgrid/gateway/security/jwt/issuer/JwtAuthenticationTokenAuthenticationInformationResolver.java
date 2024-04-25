package com.contentgrid.gateway.security.jwt.issuer;

import java.time.Instant;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

public class JwtAuthenticationTokenAuthenticationInformationResolver implements AuthenticationInformationResolver {

    @Override
    public Mono<AuthenticationInformation> resolve(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            var token = jwt.getToken();

            return Mono.just(AuthenticationInformation.builder()
                    .issuer(token.getIssuer().toString())
                    .subject(token.getSubject())
                    .expiration(Optional.ofNullable(token.getExpiresAt()).orElse(Instant.MAX))
                    .staleClaims(token)
                    .updatedClaims(Mono.just(token))
                    .build());
        }

        return Mono.empty();
    }
}
