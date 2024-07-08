package com.contentgrid.gateway.security.jwt.issuer;

import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.AuthenticationDetails;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
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
                    if (principal instanceof Authentication authentication) {
                        return createClaims(exchange, authentication);
                    }
                    return Mono.empty();
                })
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

    private Mono<JWTClaimsSet> createClaims(ServerWebExchange exchange, Authentication authentication) {
        var authenticationDetails = authentication.getAuthorities()
                .stream()
                .filter(AuthenticationDetails.class::isInstance)
                .map(AuthenticationDetails.class::cast)
                .findAny()
                .orElse(null);
        if(authenticationDetails == null) {
            return Mono.empty();
        }

        return this.jwtClaimsResolver.resolveAdditionalClaims(exchange, authenticationDetails)
                .map(claims -> {
                    var builder = new JWTClaimsSet.Builder(claims);
                    // Issuer is only available when authenticated with OIDC or bearer token.
                    // In other case, we don't set any issuer at all.
                    if(claims.getIssuer() == null) {
                        var issuer = authenticationDetails.getPrincipal().getClaims().getClaimAsString(JwtClaimNames.ISS);
                        if(issuer != null) {
                            builder.issuer(issuer);
                        }
                    }
                    // RFC 8693
                    if(authenticationDetails.getActor() != null) {
                        builder.claim("act", reconstructActorChain(authenticationDetails.getActor()));
                    }

                    return builder
                            .subject(Objects.requireNonNullElseGet(claims.getSubject(), () -> authenticationDetails.getPrincipal().getClaims().getClaimAsString(JwtClaimNames.SUB)))
                            .expirationTime(Objects.requireNonNullElseGet(claims.getExpirationTime(), () -> {
                                // Expiration is a required attribute of a JWT. If we don't have expiration information,
                                // set it to expire at the maximum validity
                                var maxExpiration = Instant.now().plus(this.maxValidity);
                                return Date.from(findExpirationTime(authentication)
                                        .filter(exp -> exp.compareTo(maxExpiration) <= 0)
                                        .orElse(maxExpiration));
                            }))
                            .issueTime(Objects.requireNonNullElseGet(claims.getIssueTime(), Date::new))
                            .build();
                });
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

    private Map<String, Object> reconstructActorChain(Actor actor) {
        var claims = new HashMap<>(actor.getClaims().getClaims());
        if(actor.getParent() != null) {
            claims.put("act", reconstructActorChain(actor.getParent()));
        }
        return claims;
    }

}
