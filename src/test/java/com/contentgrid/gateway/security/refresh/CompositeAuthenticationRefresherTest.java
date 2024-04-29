package com.contentgrid.gateway.security.refresh;

import static com.contentgrid.gateway.test.assertj.MonoAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class CompositeAuthenticationRefresherTest {
    @Test
    void respectsOrderingWhenFirstDelayed() {
        var composite = new CompositeAuthenticationRefresher(List.of(
                (auth, exchange) -> Mono.delay(Duration.of(1, ChronoUnit.SECONDS)).map(unused -> createAuthentication("1")),
                (auth, exchange) -> Mono.just(createAuthentication("2"))
        ));

        assertThat(composite.refresh(createAuthentication("principal"), createExchange()))
                .hasValueSatisfying(info -> {
                    assertThat(info.getPrincipal()).isEqualTo("1");
                });
    }


    @Test
    void fallsBackToSecondWhenFirstIsEmpty() {
        var composite = new CompositeAuthenticationRefresher(List.of(
                (auth, exchange) -> Mono.delay(Duration.of(1, ChronoUnit.SECONDS)).flatMap(unused -> Mono.empty()),
                (auth, exchange) -> Mono.just(createAuthentication("2"))
        ));

        assertThat(composite.refresh(createAuthentication("principal"), createExchange()))
                .hasValueSatisfying(info -> {
                    assertThat(info.getPrincipal()).isEqualTo("2");
                });
    }

    @Test
    void skipsSecondWhenFirstReturns() {
        var composite = new CompositeAuthenticationRefresher(List.of(
                (auth, exchange) -> Mono.delay(Duration.of(1, ChronoUnit.SECONDS)).map(unused -> createAuthentication("1")),
                (auth, exchange) -> Mono.delay(Duration.of(5, ChronoUnit.SECONDS)).map(unused -> createAuthentication("2"))
        ));

        var result = composite.refresh(createAuthentication("principal"), createExchange())
                .timeout(Duration.ofSeconds(2)); // result needs to return within 2 seconds, else timeout

        assertThat(result)
                .hasValueSatisfying(info -> {
                    assertThat(info.getPrincipal()).isEqualTo("1");
                });
    }

    private static Authentication createAuthentication(String subject) {
        return new AbstractAuthenticationToken(null) {
            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return subject;
            }
        };
    }

    private static ServerWebExchange createExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
    }


}