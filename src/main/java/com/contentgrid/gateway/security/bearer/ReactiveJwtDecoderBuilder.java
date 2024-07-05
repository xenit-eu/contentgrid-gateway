package com.contentgrid.gateway.security.bearer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder.JwkSetUriReactiveJwtDecoderBuilder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Accessors(fluent = true)
@Setter
@NoArgsConstructor(staticName = "create")
public class ReactiveJwtDecoderBuilder {

    private String issuer;
    private String jwkSetUri;
    private List<String> jwsAlgorithms = new ArrayList<>();

    public ReactiveJwtDecoderBuilder jwsAlgorithm(String jwsAlgorithm) {
        return jwsAlgorithms(List.of(jwsAlgorithm));
    }

    public ReactiveJwtDecoderBuilder jwsAlgorithms(Collection<String> jwsAlgorithms) {
        this.jwsAlgorithms.addAll(jwsAlgorithms);
        return this;
    }

    public ReactiveJwtDecoder build() {
        // Very similar to what actually happens in
        // org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerJwkConfiguration.JwtConfiguration
        JwkSetUriReactiveJwtDecoderBuilder decoderBuilder = null;
        if (jwkSetUri != null) {
            decoderBuilder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri);
        } else if (issuer != null) {
            decoderBuilder = NimbusReactiveJwtDecoder.withIssuerLocation(issuer);
        } else {
            throw new IllegalStateException(
                    "Neither jwkSetUri nor issuer are provided. Can not construct a JWT decoder.");
        }
        for (String jwsAlgorithm : jwsAlgorithms) {
            decoderBuilder.jwsAlgorithm(SignatureAlgorithm.from(jwsAlgorithm));
        }

        var decoder = decoderBuilder.build();
        var defaultValidator = issuer != null ?
                JwtValidators.createDefaultWithIssuer(issuer) :
                JwtValidators.createDefault();
        decoder.setJwtValidator(defaultValidator);
        return decoder;

    }

}
