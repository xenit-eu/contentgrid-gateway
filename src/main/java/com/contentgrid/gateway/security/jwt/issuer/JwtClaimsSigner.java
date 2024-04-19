package com.contentgrid.gateway.security.jwt.issuer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public interface JwtClaimsSigner {
    JWKSet getSigningKeys();
    SignedJWT sign(JWTClaimsSet jwtClaimsSet) throws JOSEException;
}
