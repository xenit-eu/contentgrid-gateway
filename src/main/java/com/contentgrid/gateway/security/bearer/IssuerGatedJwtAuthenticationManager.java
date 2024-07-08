package com.contentgrid.gateway.security.bearer;

import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Only authenticates a {@link BearerTokenAuthenticationToken} when the JWT is issued by a certain issuer
 */
@RequiredArgsConstructor
public class IssuerGatedJwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final Predicate<String> issuerPredicate;

    private final ReactiveAuthenticationManager delegate;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (authentication instanceof BearerTokenAuthenticationToken bearerTokenAuthenticationToken) {
            try {
                var decodedIssuer = JWTParser.parse(bearerTokenAuthenticationToken.getToken()).getJWTClaimsSet()
                        .getIssuer();
                if (decodedIssuer == null) {
                    throw new InvalidBearerTokenException("Missing issuer");
                }
                if (!issuerPredicate.test(decodedIssuer)) {
                    return Mono.empty();
                }
                return delegate.authenticate(authentication);

            } catch (ParseException e) {
                return Mono.error(new InvalidBearerTokenException(e.getMessage(), e));
            }
        }
        return Mono.empty();
    }
}
