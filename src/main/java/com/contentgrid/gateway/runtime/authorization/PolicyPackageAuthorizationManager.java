package com.contentgrid.gateway.runtime.authorization;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.isMigratedApplication;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthenticatedReactiveAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Mono;

/**
 * Skips the centralised OPA for an application without a policy package (migrated to a sidecar OPA).
 * Policy evaluation is left to the sidecar, leaving no ABAC predicate so a sidecar JWT token is minted rather than a legacy token.
 * Authentication is <em>not</em> skipped along with it: the caller must still be authenticated, so an
 * anonymous request is denied here and Spring Security can commence the OIDC login flow.
 * Other requests are delegated, so apps with a policy package still hit OPA and
 * unresolved requests keep the delegate's fail-closed deny.
 */
@Slf4j
@RequiredArgsConstructor
public class PolicyPackageAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    @NonNull
    private final ReactiveAuthorizationManager<AuthorizationContext> delegate;

    private final ReactiveAuthorizationManager<AuthorizationContext> authenticated =
            AuthenticatedReactiveAuthorizationManager.authenticated();

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext context) {
        var exchange = context.getExchange();
        if (isMigratedApplication(exchange)) {
            log.debug("No policy package for '{}': skipping OPA, requiring authentication only",
                    exchange.getRequest().getURI().getHost());
            return authenticated.check(authentication, context);
        }
        return delegate.check(authentication, context);
    }
}
