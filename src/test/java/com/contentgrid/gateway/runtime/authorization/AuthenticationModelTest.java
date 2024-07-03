package com.contentgrid.gateway.runtime.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.authorization.AuthenticationModel.ActorKind;
import com.contentgrid.gateway.runtime.authorization.AuthenticationModel.AuthenticationKind;
import com.contentgrid.gateway.runtime.security.authority.ClaimUtil;
import com.contentgrid.gateway.security.authority.UserGrantedAuthorityConverter;
import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.authority.ActorConverter;
import com.contentgrid.gateway.security.authority.DelegatedAuthenticationDetailsGrantedAuthority;
import com.contentgrid.gateway.security.authority.PrincipalAuthenticationDetailsGrantedAuthority;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.JoseHeaderNames;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthenticationModelTest {

    private static final String USER_ISSUER = "https://authentication.invalid/realms/my-user-realm";
    private static final String EXTENSION_SYSTEM_ISSUER = "https://extensions.invalid/authentication/system";
    private static final Converter<ClaimAccessor, Actor> ACTOR_CONVERTER = new ActorConverter(iss -> true,
            ActorType.USER, ClaimUtil::userClaims);

    @Test
    void fromOidcUser() {
        var instant = Instant.now();

        var idToken = OidcIdToken.withTokenValue("dummy")
                .issuer(USER_ISSUER)
                .subject("04c2cbec-faad-4dc8-ba6f-edb3d5b902e9")
                .claim(StandardClaimNames.EMAIL, "alice@wonderland.example")
                .claim(StandardClaimNames.PREFERRED_USERNAME, "alice")
                .claim("contentgrid:custom", List.of("blue", "green"))
                .issuedAt(instant)
                .expiresAt(instant.plus(5, ChronoUnit.MINUTES))
                .authTime(instant)
                // This is actually how we receive the issuer, as an URI
                .claim(IdTokenClaimNames.ISS, URI.create(USER_ISSUER))
                .authorizedParty("contentgrid-gateway")
                .build();
        var userInfo = new OidcUserInfo(idToken.getClaims());
        var userRole = new OidcUserAuthority(idToken, userInfo);
        var oidcUser = new DefaultOidcUser(Set.of(userRole), idToken, userInfo, "preferred_username");

        var converter = new UserGrantedAuthorityConverter(ACTOR_CONVERTER);

        var auth = new TestingAuthenticationToken(oidcUser, null,
                new ArrayList<>(converter.mapAuthorities(List.of(userRole))));

        var model = AuthenticationModel.from(auth);

        assertThat(model.getKind()).isEqualTo(AuthenticationKind.USER);

        assertThat(model.getPrincipal().getKind()).isEqualTo(ActorKind.USER);
        assertThat(model.getPrincipal().getClaims()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "iss", URI.create(USER_ISSUER),
                "sub", idToken.getSubject(),
                "email", "alice@wonderland.example",
                "contentgrid:custom", List.of("blue", "green")
        ));

        assertThat(model.getActor()).isNull();
    }

    @Test
    void fromJwtAccessToken() {
        var instant = Instant.now();

        var jwtToken = new Jwt(
                "X",
                Instant.now(),
                Instant.MAX,
                Map.of(
                        JoseHeaderNames.TYP, "JWT",
                        JoseHeaderNames.ALG, "none"
                ),
                Map.of(
                        "iss", USER_ISSUER,
                        "sub", "04c2cbec-faad-4dc8-ba6f-edb3d5b902e9",
                        "iat", instant,
                        "preferred_username", "alice",
                        "name", "Alice",
                        "email", "alice@wonderland.example",
                        "contentgrid:custom", List.of("blue", "green"),
                        "email_verified", false
                )
        );

        var converter = new UserGrantedAuthorityConverter(ACTOR_CONVERTER);

        var auth = new TestingAuthenticationToken(jwtToken, null, new ArrayList<>(converter.convert(jwtToken)));

        var model = AuthenticationModel.from(auth);

        assertThat(model.isAuthenticated()).isEqualTo(true);
        assertThat(model.getPrincipal().getKind()).isEqualTo(ActorKind.USER);
        assertThat(model.getPrincipal().getClaims()).containsAllEntriesOf(Map.of(
                "iss", USER_ISSUER,
                "email", "alice@wonderland.example",
                "sub", "04c2cbec-faad-4dc8-ba6f-edb3d5b902e9",
                "contentgrid:custom", List.of("blue", "green")
        ));

        assertThat(model.getActor()).isNull();
    }

    @Test
    void fromAnonymousAccessToken() {
        var anonymousToken = new AnonymousAuthenticationToken("test", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        var model = AuthenticationModel.from(anonymousToken);

        assertThat(model.isAuthenticated()).isFalse();
        assertThat(model.getKind()).isEqualTo(AuthenticationKind.ANONYMOUS);
        assertThat(model.getPrincipal()).isNull();
        assertThat(model.getActor()).isNull();
    }

    @Test
    void fromServiceAccountAccessToken() {
        var model = AuthenticationModel.from(new TestingAuthenticationToken(null, null, List.of(
                new PrincipalAuthenticationDetailsGrantedAuthority(new Actor(
                        ActorType.EXTENSION,
                        () -> Map.of(
                                "iss", EXTENSION_SYSTEM_ISSUER,
                                "sub", "extension123"
                        ),
                        null
                ))
        )));

        assertThat(model.isAuthenticated()).isTrue();
        assertThat(model.getKind()).isEqualTo(AuthenticationKind.SYSTEM);
        assertThat(model.getPrincipal().getKind()).isEqualTo(ActorKind.EXTENSION);
        assertThat(model.getPrincipal().getClaims()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "iss", EXTENSION_SYSTEM_ISSUER,
                "sub", "extension123"
        ));

        assertThat(model.getActor()).isNull();
    }

    @Test
    void fromDelegatedAccountAccessToken() {
        var model = AuthenticationModel.from(new TestingAuthenticationToken(null, null, List.of(
                new DelegatedAuthenticationDetailsGrantedAuthority(
                        new Actor(
                                ActorType.USER,
                                () -> Map.of(
                                        "iss", USER_ISSUER,
                                        "sub", "user",
                                        "contentgrid:claim1", "value1"
                                ),
                                null
                        ),
                        new Actor(
                                ActorType.EXTENSION,
                                () -> Map.of(
                                        "iss", EXTENSION_SYSTEM_ISSUER,
                                        "sub", "extension1"
                                ),
                                null
                        )
                )
        )));

        assertThat(model.isAuthenticated()).isTrue();
        assertThat(model.getKind()).isEqualTo(AuthenticationKind.DELEGATED);

        assertThat(model.getPrincipal().getKind()).isEqualTo(ActorKind.USER);
        assertThat(model.getPrincipal().getClaims()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "iss", USER_ISSUER,
                "sub", "user",
                "contentgrid:claim1", "value1"
        ));

        assertThat(model.getActor().getKind()).isEqualTo(ActorKind.EXTENSION);
        assertThat(model.getActor().getSub()).isEqualTo("extension1");

    }
}