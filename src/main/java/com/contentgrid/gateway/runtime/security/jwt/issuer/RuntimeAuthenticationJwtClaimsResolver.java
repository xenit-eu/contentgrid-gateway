package com.contentgrid.gateway.runtime.security.jwt.issuer;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter;
import com.contentgrid.gateway.security.jwt.issuer.JwtClaimsResolver;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.TextEncryptorFactory;
import com.nimbusds.jwt.JWTClaimsSet;
import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RuntimeAuthenticationJwtClaimsResolver implements JwtClaimsResolver {

    private final ApplicationConfigurationRepository applicationConfigurationRepository;

    private final TextEncryptorFactory principalClaimsEncryptor;

    private static final Predicate<String> ALLOWED_PRINCIPAL_CLAIMS = ((Predicate<String>) Set.of(JwtClaimNames.ISS, JwtClaimNames.SUB)::contains).or(
            claimName -> claimName.startsWith("contentgrid:"));

    @Override
    public Mono<JWTClaimsSet> resolveAdditionalClaims(ServerWebExchange exchange,
            AuthenticationInformation authenticationInformation) {
        ApplicationId applicationId = exchange.getRequiredAttribute(
                ContentGridAppRequestWebFilter.CONTENTGRID_APP_ID_ATTR);

        var applicationConfiguration = applicationConfigurationRepository.getApplicationConfiguration(applicationId);

        var claimsBuilder = new JWTClaimsSet.Builder();

        claimsBuilder.audience("contentgrid:system:endpoints:authentication");
        claimsBuilder.claim("context:application:id", applicationId.toString());
        if (applicationConfiguration != null) {
            claimsBuilder.claim("context:application:domains", applicationConfiguration.getDomains());
        }
        try {
            claimsBuilder.claim("restrict:principal_claims",
                    principalClaimsEncryptor.newEncryptor()
                            .encrypt(createFromClaimAccessor(authenticationInformation.getClaimAccessor()))
            );
        } catch (Exception e) {
            return Mono.error(e);
        }

        return Mono.just(claimsBuilder.build());
    }

    private static String createFromClaimAccessor(ClaimAccessor claimAccessor) {
        var builder = new JWTClaimsSet.Builder();

        claimAccessor.getClaims().forEach((name, value) -> {
            if(ALLOWED_PRINCIPAL_CLAIMS.test(name)) {
                if(value instanceof URL url) {
                    builder.claim(name, url.toString());
                } else if(value instanceof Instant instant) {
                    builder.claim(name, Date.from(instant));
                } else {
                    builder.claim(name, value);
                }
            }
        });

        return builder.build().toString();
    }
}
