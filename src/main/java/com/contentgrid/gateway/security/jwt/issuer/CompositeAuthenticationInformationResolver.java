package com.contentgrid.gateway.security.jwt.issuer;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CompositeAuthenticationInformationResolver implements AuthenticationInformationResolver {
    private final List<AuthenticationInformationResolver> resolvers;

    @Override
    public Mono<AuthenticationInformation> resolve(Authentication authentication) {
        return Flux.concat(resolvers.stream().map(resolver -> resolver.resolve(authentication)).toList())
                .next();
    }
}
