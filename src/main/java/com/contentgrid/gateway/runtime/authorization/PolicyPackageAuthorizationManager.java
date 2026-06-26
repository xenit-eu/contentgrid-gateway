package com.contentgrid.gateway.runtime.authorization;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_POLICY_PACKAGE_ATTR;
import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_SERVICE_INSTANCE_ATTR;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Skips OPA for an application without a policy package (migrated to a sidecar OPA): the request is
 * allowed without policy evaluation, leaving no ABAC predicate so the original token is forwarded. Other
 * requests are delegated, so apps with a policy package still hit OPA and unresolved requests keep the
 * delegate's fail-closed deny.
 */
@Slf4j
@RequiredArgsConstructor
public class PolicyPackageAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    @NonNull
    private final ReactiveAuthorizationManager<AuthorizationContext> delegate;

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext context) {
        var exchange = context.getExchange();
        if (isMigrated(exchange)) {
            log.debug("No policy package for '{}': skipping OPA", exchange.getRequest().getURI().getHost());
            return Mono.just(new AuthorizationDecision(true));
        }
        return delegate.check(authentication, context);
    }

    private static boolean isMigrated(ServerWebExchange exchange) {
        return exchange.getAttribute(CONTENTGRID_SERVICE_INSTANCE_ATTR) != null
                && exchange.getAttribute(CONTENTGRID_POLICY_PACKAGE_ATTR) == null;
    }
}
