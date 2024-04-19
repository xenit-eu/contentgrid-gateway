package com.contentgrid.gateway.security.jwt.issuer;

import com.contentgrid.gateway.security.jwt.issuer.JwtClaimsResolver.AuthenticationInformation;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class SignedJwtIssuer implements JwtIssuer {
    private final JwtClaimsSigner claimsSigner;
    private final JwtClaimsResolver jwtClaimsResolver;
    private final Duration maxValidity;

    public SignedJwtIssuer(JwtClaimsSigner claimsSigner, JwtClaimsResolver claimsResolver) {
        this(claimsSigner, claimsResolver, Duration.ofMinutes(5));
    }

    @Override
    public JWKSet getJwkSet() {
        return claimsSigner.getSigningKeys().toPublicJWKSet();
    }

    @Override
    public Mono<OAuth2Token> issueSubstitutionToken(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .flatMap(principal -> {
                    if (principal instanceof Authentication authentication && authentication.getPrincipal() instanceof ClaimAccessor claimAccessor) {
                        return Mono.just(AuthenticationInformation.fromClaims(claimAccessor));
                    }

                    return Mono.just(new AuthenticationInformation(
                            null,
                            principal.getName(),
                            null,
                            null
                    ));
                })
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
                                return Date.from(Optional.ofNullable(authenticationInformation.getExpiration())
                                        .filter(exp -> exp.compareTo(maxExpiration) <= 0)
                                        .orElse(maxExpiration));
                            }))
                            .issueTime(Objects.requireNonNullElseGet(claims.getIssueTime(), Date::new))
                            .build();
                });
    }

}
