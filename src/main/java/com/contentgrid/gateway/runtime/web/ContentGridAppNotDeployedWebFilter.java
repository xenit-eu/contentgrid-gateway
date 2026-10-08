package com.contentgrid.gateway.runtime.web;

import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ContentGridAppNotDeployedWebFilter implements WebFilter {

    private static final String NOT_DEPLOYED_DETAIL = "No deployment of this application is currently available";

    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    @NonNull
    private final ServerWebExchangeMatcher excludedRequests;

    @NonNull
    private final List<HttpMessageWriter<?>> messageWriters;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!ContentGridAppRequestWebFilter.isNotDeployedApplication(exchange)) {
            return chain.filter(exchange);
        }

        return this.excludedRequests.matches(exchange)
                .filter(result -> !result.isMatch())
                .flatMap(result -> ReactiveSecurityContextHolder.getContext())
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(TRUST_RESOLVER::isAuthenticated)
                .hasElement()
                .flatMap(authenticated -> authenticated ? writeProblem(exchange) : chain.filter(exchange));
    }

    private Mono<Void> writeProblem(ServerWebExchange exchange) {
        // written directly: a thrown exception would be rendered as JSON error attributes, not as a problem detail
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, NOT_DEPLOYED_DETAIL);
        return ServerResponse.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(problem)
                .flatMap(response -> response.writeTo(exchange, new ResponseContext(this.messageWriters)));
    }

    private record ResponseContext(List<HttpMessageWriter<?>> messageWriters) implements ServerResponse.Context {

        @Override
        public List<ViewResolver> viewResolvers() {
            return List.of();
        }
    }
}
