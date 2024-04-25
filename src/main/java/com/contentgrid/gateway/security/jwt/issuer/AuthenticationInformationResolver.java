package com.contentgrid.gateway.security.jwt.issuer;

import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

public interface AuthenticationInformationResolver {
    Mono<AuthenticationInformation> resolve(Authentication authentication);
}
