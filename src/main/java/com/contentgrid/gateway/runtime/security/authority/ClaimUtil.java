package com.contentgrid.gateway.runtime.security.authority;

import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

@UtilityClass
public class ClaimUtil {

    ClaimAccessor limitToKeys(ClaimAccessor original, Predicate<String> keyFilter) {
        var filteredClaims = original.getClaims()
                .entrySet()
                .stream()
                .filter(entry -> keyFilter.test(entry.getKey()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        return () -> filteredClaims;
    }

    public ClaimAccessor userClaims(ClaimAccessor original) {
        return limitToKeys(original, ClaimUtil::isUserClaim);
    }

    private boolean isUserClaim(String claimName) {
        return switch (claimName) {
            case JwtClaimNames.SUB, JwtClaimNames.ISS, StandardClaimNames.NAME, StandardClaimNames.EMAIL -> true;
            default -> claimName.startsWith("contentgrid:");
        };
    }
}
