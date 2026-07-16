package com.contentgrid.gateway.runtime.authorization;

import static com.contentgrid.thunx.spring.security.ReactivePolicyAuthorizationManager.ABAC_POLICY_PREDICATE_ATTR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.contentgrid.thunx.predicates.model.Scalar;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PolicyPackageTokenGatewayFilterTest {

    private GatewayFilter legacyMint;
    private GatewayFilter sidecarMint;
    private GatewayFilterChain chain;
    private PolicyPackageTokenGatewayFilter filter;

    @BeforeEach
    void setup() {
        this.legacyMint = mock(GatewayFilter.class);
        this.sidecarMint = mock(GatewayFilter.class);
        this.chain = mock(GatewayFilterChain.class);
        this.filter = new PolicyPackageTokenGatewayFilter(legacyMint, sidecarMint);
        lenient().when(legacyMint.filter(any(), any())).thenReturn(Mono.empty());
        lenient().when(sidecarMint.filter(any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void noAbacPredicate_mintsSidecarToken() {
        var exchange = exchange(e -> { });

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(sidecarMint).filter(exchange, chain);
        verify(legacyMint, never()).filter(any(), any());
    }

    @Test
    void abacPredicatePresent_mintsLegacyToken() {
        var exchange = exchange(e -> e.getAttributes().put(ABAC_POLICY_PREDICATE_ATTR, Scalar.of(true)));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(legacyMint).filter(exchange, chain);
        verify(sidecarMint, never()).filter(any(), any());
    }

    private static MockServerWebExchange exchange(Consumer<MockServerWebExchange> customizer) {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://foo.userapps.contentgrid.com").build());
        customizer.accept(exchange);
        return exchange;
    }
}
