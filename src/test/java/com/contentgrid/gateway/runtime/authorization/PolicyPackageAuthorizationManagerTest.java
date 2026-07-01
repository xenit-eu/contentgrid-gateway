package com.contentgrid.gateway.runtime.authorization;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_POLICY_PACKAGE_ATTR;
import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_SERVICE_INSTANCE_ATTR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.test.runtime.ServiceInstanceStubs;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PolicyPackageAuthorizationManagerTest {

    private ReactiveAuthorizationManager<AuthorizationContext> delegate;
    private PolicyPackageAuthorizationManager manager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        this.delegate = Mockito.mock(ReactiveAuthorizationManager.class);
        this.manager = new PolicyPackageAuthorizationManager(delegate);
    }

    @Test
    void migratedApplication_withoutPolicyPackage_skipsOpaAndAllows() {
        var service = ServiceInstanceStubs.serviceInstance(ApplicationId.random());
        var context = authorizationContext(exchange -> exchange.getAttributes()
                .put(CONTENTGRID_SERVICE_INSTANCE_ATTR, service));

        StepVerifier.create(manager.check(Mono.empty(), context))
                .assertNext(decision -> assertThat(decision.isGranted()).isTrue())
                .verifyComplete();

        // the OPA-backed manager must not be consulted for a migrated application
        Mockito.verifyNoInteractions(delegate);
    }

    @Test
    void applicationWithPolicyPackage_delegatesToOpa() {
        var service = ServiceInstanceStubs.serviceInstance(ApplicationId.random());
        var context = authorizationContext(exchange -> {
            exchange.getAttributes().put(CONTENTGRID_SERVICE_INSTANCE_ATTR, service);
            exchange.getAttributes().put(CONTENTGRID_POLICY_PACKAGE_ATTR, "contentgrid.userapps.example");
        });
        Mockito.when(delegate.check(any(), any())).thenReturn(Mono.just(new AuthorizationDecision(true)));

        StepVerifier.create(manager.check(Mono.empty(), context))
                .assertNext(decision -> assertThat(decision.isGranted()).isTrue())
                .verifyComplete();

        Mockito.verify(delegate).check(any(), any());
    }

    @Test
    void unresolvedApplication_delegates_preservingFailClosed() {
        // no service-instance attribute -> not a resolved app; must NOT be treated as migrated
        var context = authorizationContext(exchange -> { });
        Mockito.when(delegate.check(any(), any())).thenReturn(Mono.just(new AuthorizationDecision(false)));

        StepVerifier.create(manager.check(Mono.empty(), context))
                .assertNext(decision -> assertThat(decision.isGranted()).isFalse())
                .verifyComplete();

        Mockito.verify(delegate).check(any(), any());
    }

    private static AuthorizationContext authorizationContext(Consumer<MockServerWebExchange> customizer) {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://foo.userapps.contentgrid.com").build());
        customizer.accept(exchange);
        return new AuthorizationContext(exchange);
    }
}
