package com.contentgrid.gateway.runtime.authorization;

import static com.contentgrid.thunx.spring.security.ReactivePolicyAuthorizationManager.ABAC_POLICY_PREDICATE_ATTR;
import static org.mockito.ArgumentMatchers.any;

import com.contentgrid.thunx.predicates.model.Scalar;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PolicyPackageTokenGatewayFilterTest {

    private GatewayFilter mint;
    private GatewayFilter relay;
    private GatewayFilterChain chain;
    private PolicyPackageTokenGatewayFilter filter;

    @BeforeEach
    void setup() {
        this.mint = Mockito.mock(GatewayFilter.class);
        this.relay = Mockito.mock(GatewayFilter.class);
        this.chain = Mockito.mock(GatewayFilterChain.class);
        this.filter = new PolicyPackageTokenGatewayFilter(mint, relay);
        Mockito.lenient().when(mint.filter(any(), any())).thenReturn(Mono.empty());
        Mockito.lenient().when(relay.filter(any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void noAbacPredicate_relaysOriginalToken() {
        var exchange = exchange(e -> { });

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        Mockito.verify(relay).filter(exchange, chain);
        Mockito.verify(mint, Mockito.never()).filter(any(), any());
    }

    @Test
    void abacPredicatePresent_mintsToken() {
        var exchange = exchange(e -> e.getAttributes().put(ABAC_POLICY_PREDICATE_ATTR, Scalar.of(true)));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        Mockito.verify(mint).filter(exchange, chain);
        Mockito.verify(relay, Mockito.never()).filter(any(), any());
    }

    private static MockServerWebExchange exchange(Consumer<MockServerWebExchange> customizer) {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://foo.userapps.contentgrid.com").build());
        customizer.accept(exchange);
        return exchange;
    }
}
