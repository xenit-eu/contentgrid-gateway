package com.contentgrid.gateway.security.jwt.issuer;

import com.nimbusds.jose.jwk.JWKSet;
import java.util.Optional;

public interface JwtSignerRegistry {

    boolean hasSigner(String signerName);

    Optional<JWKSet> getJWKSet(String signerName);

    Optional<JwtClaimsSigner> getSigner(String signerName);

    default JwtClaimsSigner getRequiredSigner(String signerName) {
        return getSigner(signerName).orElseThrow(() -> new IllegalArgumentException("JWT signer %s does not exist.".formatted(signerName)));
    }
}
