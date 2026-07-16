package com.contentgrid.gateway.runtime.security.jwt.issuer;

import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.authorization.AuthenticationModel;
import com.contentgrid.gateway.runtime.security.jwt.ContentGridClaimNames;
import com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter;
import com.contentgrid.gateway.security.authority.AuthenticationDetails;
import com.contentgrid.gateway.security.jwt.issuer.JwtClaimsResolver;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTClaimsSet.Builder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Claims resolver for migrated (sidecar-authorized) applications: OPA is skipped in the gateway because the
 * appserver's own OPA sidecar performs authorization.
 */
@RequiredArgsConstructor
public class RuntimeSidecarJwtClaimsResolver implements JwtClaimsResolver {

    @Override
    public Mono<JWTClaimsSet> resolveAdditionalClaims(ServerWebExchange exchange, AuthenticationDetails authenticationDetails) {
        ApplicationId applicationId = exchange.getRequiredAttribute(ContentGridAppRequestWebFilter.CONTENTGRID_APP_ID_ATTR);
        DeploymentId deploymentId = exchange.getRequiredAttribute(ContentGridAppRequestWebFilter.CONTENTGRID_DEPLOY_ID_ATTR);

        var claimsBuilder = new Builder()
                .audience("contentgrid:app:" + applicationId + ":" + deploymentId)
                .claim(ContentGridClaimNames.AUTH_KIND, lowercaseName(AuthenticationModel.classify(authenticationDetails)))
                .claim(ContentGridClaimNames.AUTH_PRINCIPAL, createPrincipalClaim(authenticationDetails));

        return Mono.just(claimsBuilder.build());
    }

    private static Map<String, Object> createPrincipalClaim(AuthenticationDetails authenticationDetails) {
        var principal = authenticationDetails.getPrincipal();
        // Actor claims are already filtered down by the ActorConverter (see ClaimUtil::userClaims /
        // ClaimUtil::extensionSystemClaims) during authentication; serialize them verbatim, plus the kind member.
        var claims = new HashMap<>(principal.getClaims().getClaims());
        claims.put(ContentGridClaimNames.ACTOR_KIND, lowercaseName(principal.getType()));
        return claims;
    }

    private static String lowercaseName(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
