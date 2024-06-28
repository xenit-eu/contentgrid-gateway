package com.contentgrid.gateway.security.jwt.issuer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import com.contentgrid.gateway.test.security.jwt.SingleKeyJwtClaimsSigner;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.assertj.core.api.ThrowingConsumer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.context.SecurityContextServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class SignedJwtIssuerTest {
    private final JwtClaimsSigner CLAIMS_SIGNER = new SingleKeyJwtClaimsSigner();

    @Test
    void creates_derived_jwt_for_authentication_token() {
        var issuer = new SignedJwtIssuer(CLAIMS_SIGNER, JwtClaimsResolver.empty());
        var exchange = createExchange(
                new JwtAuthenticationToken(Jwt.withTokenValue("XXXX")
                        .header("alg", "RS256")
                        .claim("typ", "Bearer")
                        .issuer("https://upstream-issuer.example")
                        .subject("my-user")
                        .build())
        );

        assertThat(issuer.issueSubstitutionToken(exchange).block()).isInstanceOfSatisfying(Jwt.class, token -> {
            assertThat(token.getIssuer()).hasToString("https://upstream-issuer.example");
            assertThat(token.getSubject()).isEqualTo("my-user");
            assertThat(token.getIssuedAt()).isBeforeOrEqualTo(Instant.now());
            assertThat(token.getExpiresAt()).isBetween(Instant.now().plus(4, ChronoUnit.MINUTES), Instant.now().plus(5, ChronoUnit.MINUTES));
            assertThat(token.getTokenValue()).satisfies(verifyJwtSignedBy(issuer));
        });
    }

    @Test
    void creates_derived_jwt_for_oidc_user() {
        var issuer = new SignedJwtIssuer(CLAIMS_SIGNER, JwtClaimsResolver.empty());
        var oidcUser = new DefaultOidcUser(
                List.of(),
                OidcIdToken.withTokenValue("XXX")
                        .claim("typ", "Bearer")
                        .issuer("https://upstream-issuer.example")
                        .subject("my-user")
                        .build()
        );

        var exchange = createExchange(new OAuth2AuthenticationToken(oidcUser, null, "my-client"));

        assertThat(issuer.issueSubstitutionToken(exchange).block()).isInstanceOfSatisfying(Jwt.class, token -> {
            assertThat(token.getIssuer()).hasToString("https://upstream-issuer.example");
            assertThat(token.getSubject()).isEqualTo("my-user");
            assertThat(token.getIssuedAt()).isBeforeOrEqualTo(Instant.now());
            assertThat(token.getExpiresAt()).isBetween(Instant.now().plus(4, ChronoUnit.MINUTES), Instant.now().plus(5, ChronoUnit.MINUTES));
            assertThat(token.getTokenValue()).satisfies(verifyJwtSignedBy(issuer));
        });
    }

    @Test
    void creates_derived_jwt_for_other_authentication() {
        var issuer = new SignedJwtIssuer(CLAIMS_SIGNER, JwtClaimsResolver.empty());

        var exchange = createExchange(UsernamePasswordAuthenticationToken.authenticated("bob", null, List.of()));

        assertThat(issuer.issueSubstitutionToken(exchange).block()).isInstanceOfSatisfying(Jwt.class, token -> {
            assertThat(token.getIssuer()).isNull();
            assertThat(token.getSubject()).isEqualTo("bob");
            assertThat(token.getIssuedAt()).isBeforeOrEqualTo(Instant.now());
            assertThat(token.getExpiresAt()).isBetween(Instant.now().plus(4, ChronoUnit.MINUTES), Instant.now().plus(5, ChronoUnit.MINUTES));
            assertThat(token.getTokenValue()).satisfies(verifyJwtSignedBy(issuer));
        });
    }

    @Test
    void new_jwt_with_expiry_longer_than_max() {
        var issuer = new SignedJwtIssuer(CLAIMS_SIGNER, JwtClaimsResolver.empty());

        var exchange = createExchange(
                new JwtAuthenticationToken(Jwt.withTokenValue("XXXX")
                        .header("alg", "RS256")
                        .claim("typ", "Bearer")
                        .issuer("https://upstream-issuer.example")
                        .subject("my-user")
                        .expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                        .build())
        );
        assertThat(issuer.issueSubstitutionToken(exchange).block()).isInstanceOfSatisfying(Jwt.class, token -> {
            assertThat(token.getExpiresAt()).isBetween(Instant.now().plus(4, ChronoUnit.MINUTES), Instant.now().plus(5, ChronoUnit.MINUTES));
        });
    }

    @Test
    void new_jwt_with_expiry_shorter_than_max() {
        var issuer = new SignedJwtIssuer(CLAIMS_SIGNER, JwtClaimsResolver.empty());

        var expiry = Instant.now().plus(10, ChronoUnit.SECONDS);
        var exchange = createExchange(
                new JwtAuthenticationToken(Jwt.withTokenValue("XXXX")
                        .header("alg", "RS256")
                        .claim("typ", "Bearer")
                        .issuer("https://upstream-issuer.example")
                        .subject("my-user")
                        .expiresAt(expiry)
                        .build())
        );

        assertThat(issuer.issueSubstitutionToken(exchange).block()).isInstanceOfSatisfying(Jwt.class, token -> {
            assertThat(token.getExpiresAt()).isCloseTo(expiry, within(1, ChronoUnit.SECONDS));
        });
    }

    static ServerWebExchange createExchange(Authentication authentication) {
        var request = MockServerHttpRequest.get("/").build();
        var securityContext = new SecurityContextImpl(authentication);
        return new SecurityContextServerWebExchange(
                MockServerWebExchange.from(request),
                Mono.just(securityContext)
        );
    }

    @NotNull
    static ThrowingConsumer<String> verifyJwtSignedBy(SignedJwtIssuer issuer) {
        return tokenValue -> {
            var decoder = NimbusJwtDecoder.withPublicKey(issuer.getJwkSet().getKeys().get(0).toRSAKey()
                            .toRSAPublicKey())
                    .build();

            assertThatCode(() -> decoder.decode(tokenValue))
                    .doesNotThrowAnyException();

        };
    }

}