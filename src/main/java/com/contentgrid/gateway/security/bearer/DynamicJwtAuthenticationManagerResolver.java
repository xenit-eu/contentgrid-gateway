package com.contentgrid.gateway.security.bearer;

import com.contentgrid.gateway.runtime.RuntimeRequestResolver;
import com.contentgrid.gateway.security.oidc.ReactiveClientRegistrationIdResolver;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
public class DynamicJwtAuthenticationManagerResolver implements
        ReactiveAuthenticationManagerResolver<ServerWebExchange> {

    private final RuntimeRequestResolver runtimeRequestResolver;
    private final ReactiveClientRegistrationIdResolver reactiveClientRegistrationIdResolver;
    private final ReactiveClientRegistrationRepository clientRegistrationRepository;
    private final Map<String, Mono<ReactiveAuthenticationManager>> authenticationManagers = new ConcurrentHashMap<>();

    @Override
    public Mono<ReactiveAuthenticationManager> resolve(ServerWebExchange exchange) {

        return Mono.justOrEmpty(this.runtimeRequestResolver.resolveApplicationId(exchange))
                .flatMap(this.reactiveClientRegistrationIdResolver::resolveRegistrationId)
                .flatMap(this.clientRegistrationRepository::findByRegistrationId)
                .map(clientRegistration -> clientRegistration.getProviderDetails().getIssuerUri())
                .flatMap(issuer -> this.authenticationManagers.computeIfAbsent(issuer,
                        DynamicJwtAuthenticationManagerResolver::createJwtAuthenticationManager));
    }

    // Note: this is copied from Spring Security TrustedIssuerJwtAuthenticationManagerResolver
    private static Mono<ReactiveAuthenticationManager> createJwtAuthenticationManager(String issuer) {
        return Mono.<ReactiveAuthenticationManager>fromCallable(
                        () -> new JwtReactiveAuthenticationManager(ReactiveJwtDecoders.fromIssuerLocation(issuer)))
                .subscribeOn(Schedulers.boundedElastic())
                .cache((manager) -> Duration.ofMillis(Long.MAX_VALUE), (ex) -> Duration.ZERO, () -> Duration.ZERO);
    }
}
