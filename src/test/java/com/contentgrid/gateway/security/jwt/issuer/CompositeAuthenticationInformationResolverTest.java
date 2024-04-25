package com.contentgrid.gateway.security.jwt.issuer;


import static com.contentgrid.gateway.test.assertj.MonoAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import reactor.core.publisher.Mono;

class CompositeAuthenticationInformationResolverTest {
    @Test
    void respectsOrderingWhenFirstDelayed() {
        var composite = new CompositeAuthenticationInformationResolver(List.of(
                auth -> Mono.delay(Duration.of(1, ChronoUnit.SECONDS)).map(unused -> createAuthenticationInformation("1")),
                auth -> Mono.just(createAuthenticationInformation("2"))
        ));

        assertThat(composite.resolve(new PreAuthenticatedAuthenticationToken("principal", null)))
                .hasValueSatisfying(info -> {
                    assertThat(info.getSubject()).isEqualTo("1");
                });
    }

    @Test
    void fallsBackToSecondWhenFirstIsEmpty() {
        var composite = new CompositeAuthenticationInformationResolver(List.of(
                auth -> Mono.delay(Duration.of(1, ChronoUnit.SECONDS)).flatMap(unused -> Mono.empty()),
                auth -> Mono.just(createAuthenticationInformation("2"))
        ));

        assertThat(composite.resolve(new PreAuthenticatedAuthenticationToken("principal", null)))
                .hasValueSatisfying(info -> {
                    assertThat(info.getSubject()).isEqualTo("2");
                });
    }

    @Test
    void skipsSecondWhenFirstReturns() {
        var composite = new CompositeAuthenticationInformationResolver(List.of(
                auth -> Mono.delay(Duration.of(1, ChronoUnit.SECONDS)).map(unused -> createAuthenticationInformation("1")),
                auth -> Mono.delay(Duration.of(5, ChronoUnit.SECONDS)).map(unused -> createAuthenticationInformation("2"))
        ));

        var result = composite.resolve(new PreAuthenticatedAuthenticationToken("principal", null))
                .timeout(Duration.ofSeconds(2)); // result needs to return within 2 seconds, else timeout

        assertThat(result)
                .hasValueSatisfying(info -> {
                    assertThat(info.getSubject()).isEqualTo("1");
                });
    }

    private static AuthenticationInformation createAuthenticationInformation(String subject) {
        return AuthenticationInformation.builder()
                .issuer("test")
                .expiration(Instant.MAX)
                .subject(subject)
                .staleClaims(Map::of)
                .updatedClaims(Mono.just(Map::of))
                .build();
    }

}