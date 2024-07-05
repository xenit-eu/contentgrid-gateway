package com.contentgrid.gateway.security.bearer;

import com.contentgrid.gateway.runtime.routing.ApplicationIdRequestResolver;
import com.contentgrid.gateway.security.oidc.ReactiveClientRegistrationIdResolver;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@RequiredArgsConstructor
public class DynamicJwtAuthenticationManagerResolver implements
        ReactiveAuthenticationManagerResolver<ServerWebExchange> {

    private final ApplicationIdRequestResolver applicationIdResolver;
    private final ReactiveClientRegistrationIdResolver reactiveClientRegistrationIdResolver;
    private final ReactiveClientRegistrationRepository clientRegistrationRepository;
    @Setter
    private Consumer<JwtReactiveAuthenticationManager> authenticationManagerConfigurer = jwtReactiveAuthenticationManager -> {};
    private final Map<AuthenticationManagerKey, Mono<ReactiveAuthenticationManager>> authenticationManagers = new ConcurrentHashMap<>();

    private record AuthenticationManagerKey(
            @NonNull String issuer,
            String jwkSetUri
    ) {

    }

    @Override
    public Mono<ReactiveAuthenticationManager> resolve(ServerWebExchange exchange) {

        return Mono.justOrEmpty(this.applicationIdResolver.resolveApplicationId(exchange))
                .flatMap(this.reactiveClientRegistrationIdResolver::resolveRegistrationId)
                .flatMap(this.clientRegistrationRepository::findByRegistrationId)
                .map(clientRegistration -> new AuthenticationManagerKey(
                        clientRegistration.getProviderDetails().getIssuerUri(),
                        clientRegistration.getProviderDetails().getJwkSetUri()))
                .flatMap(authenticationManagerKey -> this.authenticationManagers.computeIfAbsent(
                        authenticationManagerKey, this::createJwtAuthenticationManager))
                .checkpoint()

                // could not resolve request to an app-id or related client-registration
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Resolving authentication manager for {} failed", exchange.getRequest().getURI());
                    return Mono.just(authentication -> Mono.error(new InvalidBearerTokenException("no jwt auth manager found")));
                }));
    }

    // Note: this is copied from Spring Security TrustedIssuerJwtAuthenticationManagerResolver
    private Mono<ReactiveAuthenticationManager> createJwtAuthenticationManager(
            AuthenticationManagerKey authenticationManagerKey) {
        return Mono.<ReactiveAuthenticationManager>fromCallable(() -> {
                    var authenticationManager = new JwtReactiveAuthenticationManager(
                            createJwtDecoder(authenticationManagerKey));
                    authenticationManagerConfigurer.accept(authenticationManager);
                    return authenticationManager;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .cache(manager -> Duration.ofMillis(Long.MAX_VALUE), ex -> Duration.ZERO, () -> Duration.ZERO);
    }

    private static ReactiveJwtDecoder createJwtDecoder(AuthenticationManagerKey authenticationManagerKey) {
        return ReactiveJwtDecoderBuilder.create()
                .issuer(authenticationManagerKey.issuer())
                .jwkSetUri(authenticationManagerKey.jwkSetUri())
                .build();
    }
}
