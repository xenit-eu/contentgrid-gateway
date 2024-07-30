package com.contentgrid.gateway.security.jwt.issuer;

import com.nimbusds.jose.JWSAlgorithm;
import java.util.Set;

public interface JwtClaimsSignerProperties {

    String getActiveKeys();

    String getRetiredKeys();

    Set<JWSAlgorithm> getAlgorithms();
}
