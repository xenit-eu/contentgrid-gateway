package com.contentgrid.gateway.runtime.security.bearer;

import com.contentgrid.gateway.runtime.security.jwt.ContentGridClaimNames;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Scaffold for the "Gateway extension" requirement that an extension token's
 * {@value ContentGridClaimNames#CONTEXT_APPLICATION_RESOURCE} claim, when present, must match the request
 * URL exactly.
 * <p>
 * TODO: this only does a strict string-equals of the raw path+query captured at request time. The spec
 * ("{@code context:application:resource} matches the request URL exactly, if present") doesn't define
 * whether query string parameter ordering, percent-encoding, or trailing slashes should be normalized
 * before comparing — needs to be nailed down before this can be relied on for real traffic.
 *
 * @see <a href="https://github.com/xenit-eu/contentgrid-system-design/blob/main/architecture/specs/extension-authentication.md#gateway-extension">Automation extension authentication spec</a>
 */
@RequiredArgsConstructor
public class ExtensionResourceJwtClaimValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error MISMATCH_ERROR = new OAuth2Error(
            "invalid_token",
            "The '%s' claim does not match the request".formatted(ContentGridClaimNames.CONTEXT_APPLICATION_RESOURCE),
            null
    );

    /**
     * The request's own resource (URI without origin, see {@link ContentGridClaimNames#CONTEXT_APPLICATION_RESOURCE}),
     * captured once per request so it can be compared against whatever token ends up being validated.
     */
    private final String requestResource;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        var claimedResource = token.getClaimAsString(ContentGridClaimNames.CONTEXT_APPLICATION_RESOURCE);
        if (claimedResource == null || claimedResource.equals(requestResource)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(MISMATCH_ERROR);
    }
}
