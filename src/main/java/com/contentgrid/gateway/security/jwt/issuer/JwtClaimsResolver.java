package com.contentgrid.gateway.security.jwt.issuer;

import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.Delegate;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface JwtClaimsResolver {
    Mono<JWTClaimsSet> resolveAdditionalClaims(ServerWebExchange exchange, AuthenticationInformation authenticationInformation);

    static JwtClaimsResolver empty() {
        return (exchange, authenticationInformation) -> Mono.just(new JWTClaimsSet.Builder().build());
    }

    @Value
    @Builder(access = AccessLevel.PRIVATE)
    class AuthenticationInformation {
        String issuer;
        String subject;
        Instant expiration;
        @Delegate
        ClaimAccessor claimAccessor;

        static AuthenticationInformation fromClaims(ClaimAccessor claimAccessor) {
            return AuthenticationInformation.builder()
                    .issuer(claimAccessor.getClaimAsString(JwtClaimNames.ISS))
                    .subject(claimAccessor.getClaimAsString(JwtClaimNames.SUB))
                    .expiration(claimAccessor.getClaimAsInstant(JwtClaimNames.EXP))
                    .claimAccessor(claimAccessor)
                    .build();
        }
    }
}
