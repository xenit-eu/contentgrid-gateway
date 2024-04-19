package com.contentgrid.gateway.security.jwt.issuer.actuate;

import com.nimbusds.jose.jwk.JWKSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

@WebEndpoint(id = "jwks")
@RequiredArgsConstructor
public class JWKSetEndpoint {
    private final Function<String, Optional<JWKSet>> jwkSetProvider;

    @ReadOperation(produces = JWKSet.MIME_TYPE)
    public WebEndpointResponse<Map<String, Object>> jwkSet(@Selector String signerName) {
        return jwkSetProvider.apply(signerName)
                .map(JWKSet::toJSONObject)
                .map(WebEndpointResponse::new)
                .orElse(new WebEndpointResponse<>(404));
    }

}
