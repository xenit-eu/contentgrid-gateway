package com.contentgrid.gateway.security.jwt.issuer;

import com.nimbusds.jose.jwk.JWKSet;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ObservationJwtIssuer implements JwtIssuer {

    private final ObservationRegistry registry;

    private final JwtIssuer delegate;

    @Setter
    private ObservationConvention<JwtIssuerContext> convention = new ContextObservationConvention();

    @Override
    public JWKSet getJwkSet() {
        return delegate.getJwkSet();
    }

    @Override
    public Mono<OAuth2Token> issueSubstitutionToken(ServerWebExchange exchange) {
        var context = new JwtIssuerContext();
        return exchange.getPrincipal().doOnNext(principal -> {
            if (principal instanceof Authentication authentication) {
                findExpirationTime(authentication).ifPresent(context::setExpirationTime);
            }
        }).then(
                Mono.deferContextual(contextView -> {
                    Observation observation = Observation.createNotStarted(this.convention, () -> context,
                                    this.registry)
                            .parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null))
                            .start();
                    return this.delegate.issueSubstitutionToken(exchange).doOnSuccess(result -> {
                        context.setToken(result);
                        observation.stop();
                    }).doOnCancel(observation::stop).doOnError(t -> {
                        observation.error(t);
                        observation.stop();
                    });
                })
        );
    }

    private Optional<Instant> findExpirationTime(Authentication authentication) {
        var principal = authentication.getPrincipal();
        if(principal instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getExpiresAt());
        } else if(principal instanceof OidcUser oidcUser) {
            return Optional.ofNullable(oidcUser.getIdToken().getExpiresAt());
        }
        return Optional.empty();
    }


    @EqualsAndHashCode(callSuper = true)
    @Data
    private static class JwtIssuerContext extends Context {
        private Instant expirationTime;
        private OAuth2Token token;
    }

    private static class ContextObservationConvention implements ObservationConvention<JwtIssuerContext> {

        @Override
        public boolean supportsContext(Context context) {
            return context instanceof JwtIssuerContext;
        }

        @Override
        public String getName() {
            return "jwt-issuer";
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(JwtIssuerContext context) {
            return KeyValues.of("incomingToken.expirationTime", Objects.toString(context.getExpirationTime()))
                    .and("outgoingToken.issued", Boolean.toString(context.getToken() != null))
                    .and("outgoingToken.expirationTime", Objects.toString(context.getToken() != null ? context.getToken().getExpiresAt() : null))
                    .and("outgoingToken.issuedAt", Objects.toString(context.getToken() != null ? context.getToken().getIssuedAt() : null));
        }
    }
}
