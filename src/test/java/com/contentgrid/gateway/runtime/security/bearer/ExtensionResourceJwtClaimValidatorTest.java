package com.contentgrid.gateway.runtime.security.bearer;

import static com.contentgrid.gateway.runtime.security.jwt.ContentGridClaimNames.CONTEXT_APPLICATION_RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;

class ExtensionResourceJwtClaimValidatorTest {

    private static Jwt jwtWithResourceClaim(String resourceClaim) {
        var claims = new HashMap<String, Object>();
        claims.put("sub", "extension");
        if (resourceClaim != null) {
            claims.put(CONTEXT_APPLICATION_RESOURCE, resourceClaim);
        }
        return new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), claims);
    }

    @ParameterizedTest
    @CsvSource({
            "/foo/bar?baz=1, ",
            "/foo/bar?baz=1, /foo/bar?baz=1",
            "/foo/bar,       /foo/bar",
    })
    void resourceClaimAbsentOrMatching_succeeds(String requestResource, String resourceClaim) {
        var validator = new ExtensionResourceJwtClaimValidator(requestResource);

        var result = validator.validate(jwtWithResourceClaim(resourceClaim));

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getErrors()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "/foo/bar,       /other/path",
            "/foo/bar?baz=1, /foo/bar?baz=2",
            "/foo/bar,       /foo/bar?baz=1",
            "/foo/bar?baz=1, /foo/bar",
    })
    void resourceClaimMismatch_failsWithInvalidTokenError(String requestResource, String resourceClaim) {
        var validator = new ExtensionResourceJwtClaimValidator(requestResource);

        var result = validator.validate(jwtWithResourceClaim(resourceClaim));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
            assertThat(error.getDescription())
                    .isEqualTo("The 'context:application:resource' claim does not match the request");
        });
    }
}
