package com.contentgrid.gateway.security.jwt.issuer;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.springframework.security.oauth2.core.ClaimAccessor;
import reactor.core.publisher.Mono;

@Value
@Builder
public
class AuthenticationInformation {

    String issuer;

    @NonNull
    String subject;

    @NonNull
    Instant expiration;

    @Builder.Default
    @NonNull
    ClaimAccessor staleClaims = Map::of;

    @Builder.Default
    @NonNull
    Mono<ClaimAccessor> updatedClaims = Mono.just(Map::of);
}
