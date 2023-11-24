package com.contentgrid.gateway.runtime.authorization;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

@Value
@Builder
public class AuthenticationModel {
    boolean authenticated;

    Map<String, Object> principal;

    String issuer;
    @JsonProperty("authenticated_at")
    Instant authenticatedAt;
    String acr;

    public static AuthenticationModel from(Authentication authenticationContext) {
        var claims = extractClaims(authenticationContext);
        return AuthenticationModel.builder()
                .authenticated(!(authenticationContext instanceof AnonymousAuthenticationToken))
                .principal(createPrincipal(claims))
                .issuer(claims.getClaimAsString("iss"))
                .authenticatedAt(claims.getClaimAsInstant("auth_time"))
                .acr(claims.getClaimAsString("acr"))
                .build();
    }

    private static Map<String, Object> createPrincipal(ClaimAccessor claims) {
        var principal = new HashMap<String, Object>();

        for (String claimName : claims.getClaims().keySet()) {
            switch (claimName) {
                case "preferred_username" -> principal.put("username", claims.getClaimAsString(claimName));
                case "email", "sub" -> principal.put(claimName, claims.getClaimAsString(claimName));
            }
            if(claimName.startsWith("contentgrid:")) {
                principal.put(claimName, claims.getClaim(claimName));
            }
        }

        return principal;
    }

    private static ClaimAccessor extractClaims(Authentication authenticationContext) {
        var principal = authenticationContext.getPrincipal();

        if(principal instanceof ClaimAccessor claimAccessor) {
            return claimAccessor;
        }

        // fallback to check authorities on the auth-object
        var claims =  authenticationContext.getAuthorities().stream()
                .filter(OAuth2UserAuthority.class::isInstance)
                .map(OAuth2UserAuthority.class::cast)
                .findAny()
                .map(OAuth2UserAuthority::getAttributes)
                .orElse(Map.of());
        return () -> claims;
    }
}
