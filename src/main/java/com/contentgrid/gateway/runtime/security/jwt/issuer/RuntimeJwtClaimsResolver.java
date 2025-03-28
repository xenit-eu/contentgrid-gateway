package com.contentgrid.gateway.runtime.security.jwt.issuer;

import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter;
import com.contentgrid.gateway.security.authority.AuthenticationDetails;
import com.contentgrid.gateway.security.jwt.issuer.JwtClaimsResolver;
import com.contentgrid.thunx.encoding.ThunkExpressionEncoder;
import com.contentgrid.thunx.encoding.json.JsonThunkExpressionCoder;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.spring.security.ReactivePolicyAuthorizationManager;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTClaimsSet.Builder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RuntimeJwtClaimsResolver implements JwtClaimsResolver {
    private static final ThunkExpressionEncoder thunxExpressionEncoder = new JsonThunkExpressionCoder();

    @Override
    public Mono<JWTClaimsSet> resolveAdditionalClaims(ServerWebExchange exchange, AuthenticationDetails authenticationDetails) {
        ApplicationId applicationId = exchange.getRequiredAttribute(ContentGridAppRequestWebFilter.CONTENTGRID_APP_ID_ATTR);
        DeploymentId deploymentId = exchange.getRequiredAttribute(ContentGridAppRequestWebFilter.CONTENTGRID_DEPLOY_ID_ATTR);
        ThunkExpression<Boolean> abacPolicyPredicate = exchange.getAttribute(ReactivePolicyAuthorizationManager.ABAC_POLICY_PREDICATE_ATTR);

        var jwtClaimsBuilder = new Builder();

        if(abacPolicyPredicate != null) {
            jwtClaimsBuilder.claim("x-abac-context", new String(thunxExpressionEncoder.encode(abacPolicyPredicate), StandardCharsets.UTF_8));
        }

        return Mono.just(
                jwtClaimsBuilder
                        .audience("contentgrid:app:"+applicationId+":"+deploymentId)
                        .claim(StandardClaimNames.NAME, authenticationDetails.getPrincipal().getClaims().getClaimAsString(StandardClaimNames.NAME))
                        .build()
        );
    }
}
