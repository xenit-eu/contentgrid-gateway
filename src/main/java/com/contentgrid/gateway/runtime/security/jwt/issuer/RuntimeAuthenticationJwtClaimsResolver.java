package com.contentgrid.gateway.runtime.security.jwt.issuer;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.security.jwt.ContentGridAudiences;
import com.contentgrid.gateway.runtime.security.jwt.ContentGridClaimNames;
import com.contentgrid.gateway.security.authority.AuthenticationDetails;
import com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter;
import com.contentgrid.gateway.security.jwt.issuer.JwtClaimsResolver;
import com.contentgrid.gateway.security.jwt.issuer.encrypt.TextEncryptorFactory;
import com.nimbusds.jwt.JWTClaimsSet;
import java.net.URL;
import java.time.Instant;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RuntimeAuthenticationJwtClaimsResolver implements JwtClaimsResolver {

    private final ApplicationConfigurationRepository applicationConfigurationRepository;

    private final TextEncryptorFactory principalClaimsEncryptor;

    @Override
    public Mono<JWTClaimsSet> resolveAdditionalClaims(ServerWebExchange exchange, AuthenticationDetails authenticationDetails) {
        ApplicationId applicationId = exchange.getRequiredAttribute(
                ContentGridAppRequestWebFilter.CONTENTGRID_APP_ID_ATTR);

        var applicationConfiguration = applicationConfigurationRepository.getApplicationConfiguration(applicationId);

        var claimsBuilder = new JWTClaimsSet.Builder();

        claimsBuilder.audience(ContentGridAudiences.SYSTEM_ENDPOINT_AUTHENTICATION);
        claimsBuilder.claim(ContentGridClaimNames.CONTEXT_APPLICATION_ID, applicationId.toString());
        if (applicationConfiguration != null) {
            claimsBuilder.claim(ContentGridClaimNames.CONTEXT_APPLICATION_DOMAINS, applicationConfiguration.getDomains());
        }
        try {
            claimsBuilder.claim(ContentGridClaimNames.RESTRICT_PRINCIPAL_CLAIMS,
                    principalClaimsEncryptor.newEncryptor()
                            .encrypt(createFromClaimAccessor(authenticationDetails.getPrincipal().getClaims()))
            );
        } catch (Exception e) {
            return Mono.error(e);
        }

        return Mono.just(claimsBuilder.build());
    }

    private static String createFromClaimAccessor(ClaimAccessor claimAccessor) {
        var builder = new JWTClaimsSet.Builder();

        claimAccessor.getClaims().forEach((name, value) -> {
            if(value instanceof URL url) {
                builder.claim(name, url.toString());
            } else if(value instanceof Instant instant) {
                builder.claim(name, Date.from(instant));
            } else {
                builder.claim(name, value);
            }
        });

        return builder.build().toString();
    }
}
