package com.contentgrid.gateway.security.opa;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

class AuthenticationModelTest {

    @Test
    void fromOidcUser() {
        var instant = Instant.now();

        var idToken = OidcIdToken.withTokenValue("dummy")
                .subject("04c2cbec-faad-4dc8-ba6f-edb3d5b902e9")
                .claim("preferred_username", "alice")
                .issuedAt(instant)
                .expiresAt(instant.plus(5, ChronoUnit.MINUTES))
                .authTime(instant)
                // This is actually how we receive the issuer, as an URI
                .claim(IdTokenClaimNames.ISS, URI.create("https://auth.contentgrid.com/auth/realms/my-org"))
                .authorizedParty("contentgrid-gateway")
                .build();
        var userInfo = new OidcUserInfo(Map.of(
                "contentgrid:custom", List.of("blue", "green"),
                "email_verified", false
        ));
        var userRole = new OidcUserAuthority(idToken, userInfo);
        var oidcUser = new DefaultOidcUser(Set.of(userRole), idToken, userInfo, "preferred_username");

        var auth = new TestingAuthenticationToken(oidcUser, null, AuthorityUtils.NO_AUTHORITIES);

        var model = AuthenticationModel.from(auth);

        assertThat(model.isAuthenticated()).isEqualTo(true);

        assertThat(model.getPrincipal()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "username", "alice",
                "sub", idToken.getSubject(),
                "contentgrid:custom", List.of("blue", "green")
        ));

        assertThat(model.getAuthenticatedAt()).isEqualTo(instant);
        assertThat(model.getIssuer()).isEqualTo("https://auth.contentgrid.com/auth/realms/my-org");
    }

    @Test
    void fromJwtAccessToken() {
        var instant = Instant.now();

        var jwtToken = new ClaimAccessor() {

            @Override
            public Map<String, Object> getClaims() {
                return Map.of("sub", "04c2cbec-faad-4dc8-ba6f-edb3d5b902e9",
                        "iat", instant,
                        "preferred_username", "alice",
                        "contentgrid:custom", List.of("blue", "green"),
                        "email_verified", false
                );

            }
        };
        var auth = new TestingAuthenticationToken(jwtToken, null, AuthorityUtils.NO_AUTHORITIES);

        var model = AuthenticationModel.from(auth);

        assertThat(model.isAuthenticated()).isEqualTo(true);
        assertThat(model.getPrincipal()).containsAllEntriesOf(Map.of(
                "username", "alice",
                "sub", "04c2cbec-faad-4dc8-ba6f-edb3d5b902e9",
                "contentgrid:custom", List.of("blue", "green")
        ));
    }

    @Test
    void fromAnonymousAccessToken() {
        var anonymousToken = new AnonymousAuthenticationToken("test", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        var model = AuthenticationModel.from(anonymousToken);

        assertThat(model.isAuthenticated()).isFalse();
        assertThat(model.getAuthenticatedAt()).isNull();
        assertThat(model.getIssuer()).isNull();
        assertThat(model.getAcr()).isNull();
        assertThat(model.getPrincipal()).isEmpty();
    }
}