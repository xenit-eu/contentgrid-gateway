package com.contentgrid.gateway.security.bearer;

import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class PostValidatingJwtAuthenticationManager<T> implements ReactiveAuthenticationManager{
    private final JwtClaimValidator<T> validator;
    private final ReactiveAuthenticationManager delegate;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (authentication instanceof BearerTokenAuthenticationToken) {
            return delegate.authenticate(authentication)
                    .filter(JwtAuthenticationToken.class::isInstance)
                    .doOnNext(authenticationToken -> validateAuthenticationToken(((JwtAuthenticationToken)authenticationToken).getToken()));
        }
        return null;
    }

    private void validateAuthenticationToken(Jwt jwt) {
        var validationResult = validator.validate(jwt);
        if(validationResult.hasErrors()) {
            var ex = new JwtValidationException(
                    validationResult.getErrors().stream()
                            .map(OAuth2Error::getDescription)
                            .filter(Predicate.not(StringUtils::hasLength))
                            .findFirst()
                            .orElse("Unable to validate Jwt."),
                    validationResult.getErrors()
            );
            throw new InvalidBearerTokenException(ex.getMessage(), ex);
        }
    }
}
