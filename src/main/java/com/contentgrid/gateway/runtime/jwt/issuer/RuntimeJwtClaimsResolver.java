package com.contentgrid.gateway.runtime.jwt.issuer;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter;
import com.contentgrid.gateway.security.jwt.issuer.JwtClaimsResolver;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTClaimsSet.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RuntimeJwtClaimsResolver implements JwtClaimsResolver {
    @Override
    public Mono<JWTClaimsSet> resolveAdditionalClaims(ServerWebExchange exchange, AuthenticationInformation authenticationInformation) {
        ApplicationId applicationId = exchange.getRequiredAttribute(ContentGridAppRequestWebFilter.CONTENTGRID_APP_ID_ATTR);
        DeploymentId deploymentId = exchange.getRequiredAttribute(ContentGridAppRequestWebFilter.CONTENTGRID_DEPLOY_ID_ATTR);

        return Mono.just(
                new Builder()
                        .audience("contentgrid:app:"+applicationId+":"+deploymentId)
                        .claim(StandardClaimNames.NAME, authenticationInformation.getClaim(StandardClaimNames.NAME))
                        .claim("act", authenticationInformation.getClaim("act")) // RFC 8693
                        .build()
        );
    }
}
