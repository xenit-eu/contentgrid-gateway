package com.contentgrid.gateway.security.jwt.issuer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import java.security.Principal;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class SignedJwtIssuer implements JwtIssuer {

    private final JwtClaimsSigner claimsSigner;
    private final JwtClaimsResolver jwtClaimsResolver;
    private final AuthenticationInformationResolver authenticationInformationResolver;
    private final Duration maxValidity;

    public SignedJwtIssuer(JwtClaimsSigner claimsSigner, JwtClaimsResolver claimsResolver, AuthenticationInformationResolver authenticationInformationResolver) {
        this(claimsSigner, claimsResolver, authenticationInformationResolver, Duration.ofMinutes(5));
    }

    @Override
    public JWKSet getJwkSet() {
        return claimsSigner.getSigningKeys().toPublicJWKSet();
    }


    @Override
    public Mono<OAuth2Token> issueSubstitutionToken(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .flatMap(this::resolveAuthenticationInformation)
                .flatMap(authenticationInformation -> createClaims(exchange, authenticationInformation))
                .flatMap(claims -> {
                    try {
                        return Mono.just(claimsSigner.sign(claims));
                    } catch (JOSEException e) {
                        return Mono.error(e);
                    }
                })
                .flatMap(signedJwt -> {
                    try {
                        var signedJwtClaims = signedJwt.getJWTClaimsSet().getClaims();
                        return Mono.just(Jwt.withTokenValue(signedJwt.serialize())
                                .headers(headers -> headers.putAll(signedJwt.getHeader().toJSONObject()))
                                .claims(claims -> claims.putAll(signedJwtClaims))
                                // These two are special; spring requires them to be Instant
                                .issuedAt(Optional.ofNullable(signedJwt.getJWTClaimsSet().getIssueTime()).map(Date::toInstant).orElse(null))
                                .expiresAt(Optional.ofNullable(signedJwt.getJWTClaimsSet().getExpirationTime()).map(Date::toInstant).orElse(null))
                                .build());
                    } catch (ParseException e) {
                        return Mono.error(e);
                    }
                });
    }

    private Mono<AuthenticationInformation> resolveAuthenticationInformation(Principal principal) {
        return Mono.just(principal)
                .ofType(Authentication.class)
                .flatMap(authenticationInformationResolver::resolve)
                .switchIfEmpty(Mono.just(AuthenticationInformation.builder()
                                .subject(principal.getName())
                                .expiration(Instant.now().plus(maxValidity))
                        .build())
                );
    }

    private Mono<JWTClaimsSet> createClaims(ServerWebExchange exchange, AuthenticationInformation authenticationInformation) {

        return this.jwtClaimsResolver.resolveAdditionalClaims(exchange, authenticationInformation)
                .map(claims -> {
                    var builder = new JWTClaimsSet.Builder(claims);
                    // Issuer is only available when authenticated with OIDC or bearer token.
                    // In other case, we don't set any issuer at all.
                    if(claims.getIssuer() == null && authenticationInformation.getIssuer() != null) {
                        builder.issuer(authenticationInformation.getIssuer());
                    }
                    return builder
                            .subject(Objects.requireNonNullElseGet(claims.getSubject(), authenticationInformation::getSubject))
                            .expirationTime(Objects.requireNonNullElseGet(claims.getExpirationTime(), () -> {
                                // Expiration is a required attribute of a JWT. If we don't have expiration information,
                                // set it to expire at the maximum validity
                                var maxExpiration = Instant.now().plus(this.maxValidity);
                                if(authenticationInformation.getExpiration().compareTo(maxExpiration) <= 0) {
                                    return Date.from(authenticationInformation.getExpiration());
                                }
                                return Date.from(maxExpiration);
                            }))
                            .issueTime(Objects.requireNonNullElseGet(claims.getIssueTime(), Date::new))
                            .build();
                });
    }

}
