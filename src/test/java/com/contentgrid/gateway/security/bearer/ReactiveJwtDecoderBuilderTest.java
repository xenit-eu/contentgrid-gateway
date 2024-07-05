package com.contentgrid.gateway.security.bearer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.gateway.security.jwt.issuer.JwtClaimsSigner;
import com.contentgrid.gateway.test.security.jwt.SingleKeyJwtClaimsSigner;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;

class ReactiveJwtDecoderBuilderTest {

    private static final WireMockServer wireMockServer = new WireMockServer(
            new WireMockConfiguration().dynamicPort());

    private static final JwtClaimsSigner ISSUER_JWT_SIGNER = new SingleKeyJwtClaimsSigner();

    private static final JwtClaimsSigner JWKS_SIGNER = new SingleKeyJwtClaimsSigner();

    @BeforeAll
    static void startWiremock() {
        wireMockServer.start();
        wireMockServer.stubFor(WireMock.get("/issuer/.well-known/openid-configuration").willReturn(WireMock.okJson("""
                {
                    "issuer": "{baseUrl}/issuer",
                    "jwks_uri": "{baseUrl}/issuer/jwks"
                }
                """.replace("{baseUrl}", wireMockServer.baseUrl()))));

        wireMockServer.stubFor(WireMock.get("/issuer/jwks")
                .willReturn(WireMock.okJson(ISSUER_JWT_SIGNER.getSigningKeys().toString())));
        wireMockServer.stubFor(
                WireMock.get("/direct/jwks").willReturn(WireMock.okJson(JWKS_SIGNER.getSigningKeys().toString())));
    }

    @AfterAll
    static void shutdownWiremock() {
        wireMockServer.stop();
    }

    @SneakyThrows
    private static String sign(JwtClaimsSigner signer, Map<String, Object> claims) {
        return signer.sign(JWTClaimsSet.parse(claims)).serialize();
    }

    @Test
    void buildFromIssuerOnly() {
        var decoder = ReactiveJwtDecoderBuilder.create()
                .issuer(wireMockServer.url("issuer"))
                .build();

        Map<String, Object> tokenClaims = Map.of(
                "iss", wireMockServer.url("issuer"),
                "sub", "test123"
        );

        // Valid signed JWT
        String validIssuerJwt = sign(ISSUER_JWT_SIGNER, tokenClaims);
        assertThatCode(() -> decoder.decode(validIssuerJwt).block())
                .doesNotThrowAnyException();

        // Invalid signed JWT (incorrect signing key)
        String invalidSignedJwt = sign(JWKS_SIGNER, tokenClaims);
        assertThatThrownBy(() -> decoder.decode(invalidSignedJwt).block())
                .isInstanceOf(BadJwtException.class);

        // Invalid signed JWT (incorrect issuer)
        String invalidIssuerJwt = sign(ISSUER_JWT_SIGNER, Map.of(
                "iss", "https://example.invalid",
                "sub", "xxx"
        ));
        assertThatThrownBy(() -> decoder.decode(invalidIssuerJwt).block())
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void buildFromJwksOnly() {
        var decoder = ReactiveJwtDecoderBuilder.create()
                .jwkSetUri(wireMockServer.url("direct/jwks"))
                .build();

        Map<String, Object> tokenClaims = Map.of(
                "iss", wireMockServer.url("issuer"),
                "sub", "test123"
        );

        // Valid signed JWT (correct signing key)
        String validSignedJwt = sign(JWKS_SIGNER, tokenClaims);
        assertThatCode(() -> decoder.decode(validSignedJwt).block())
                .doesNotThrowAnyException();

        // Valid signed JWT (other issuer)
        String validOtherIssuerJwt = sign(JWKS_SIGNER, Map.of(
                "iss", "https://example.invalid",
                "sub", "xxx"
        ));
        assertThatCode(() -> decoder.decode(validOtherIssuerJwt).block())
                .doesNotThrowAnyException();

        // Invalid signed JWT (incorrect signing key)
        String invalidSignedJwt = sign(ISSUER_JWT_SIGNER, tokenClaims);
        assertThatThrownBy(() -> decoder.decode(invalidSignedJwt).block())
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void buildFromJwksUriAndIssuer() {
        var decoder = ReactiveJwtDecoderBuilder.create()
                .issuer(wireMockServer.url("issuer"))
                .jwkSetUri(wireMockServer.url("direct/jwks"))
                .build();

        Map<String, Object> tokenClaims = Map.of(
                "iss", wireMockServer.url("issuer"),
                "sub", "test123"
        );

        // Valid signed JWT
        String validSignedJwt = sign(JWKS_SIGNER, tokenClaims);
        assertThatCode(() -> decoder.decode(validSignedJwt).block())
                .doesNotThrowAnyException();

        // Invalid signed JWT (incorrect signing key)
        String invalidSignedJwt = sign(ISSUER_JWT_SIGNER, tokenClaims);
        assertThatThrownBy(() -> decoder.decode(invalidSignedJwt).block())
                .isInstanceOf(BadJwtException.class);

        // Invalid signed JWT (incorrect issuer)
        String invalidIssuerJwt = sign(JWKS_SIGNER, Map.of(
                "iss", "https://example.invalid",
                "sub", "xxx"
        ));
        assertThatThrownBy(() -> decoder.decode(invalidIssuerJwt).block())
                .isInstanceOf(BadJwtException.class);
    }

}