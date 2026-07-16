package com.contentgrid.gateway.runtime.security.jwt.issuer;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_APP_ID_ATTR;
import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_DEPLOY_ID_ATTR;
import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.authority.AuthenticationDetails;
import com.contentgrid.gateway.security.authority.DelegatedAuthenticationDetailsGrantedAuthority;
import com.contentgrid.gateway.security.authority.PrincipalAuthenticationDetailsGrantedAuthority;
import com.contentgrid.gateway.test.security.jwt.ExpectedClaims;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RuntimeSidecarJwtClaimsResolverTest {

    private static final ApplicationId APPLICATION_ID = ApplicationId.from("a1");
    private static final DeploymentId DEPLOYMENT_ID = DeploymentId.from("d1");

    private static Actor userActor() {
        return new Actor(
                ActorType.USER,
                () -> Map.of("iss", "https://upstream-issuer.example", "sub", "my-user", "name", "My User"),
                null
        );
    }

    private static Actor extensionActor() {
        return new Actor(
                ActorType.EXTENSION,
                () -> Map.of("iss", "https://extensions.invalid/authentication/system", "sub", "extension123"),
                null
        );
    }

    @Test
    void leavesIssuerUnset() {
        var resolver = new RuntimeSidecarJwtClaimsResolver();
        var authenticationDetails = new PrincipalAuthenticationDetailsGrantedAuthority(userActor());

        var claims = resolver.resolveAdditionalClaims(exchange(), authenticationDetails).block();

        assertThat(claims).isNotNull();
        assertThat(claims.getIssuer()).isNull();
    }

    static Stream<Arguments> assertClaims() {
        return Stream.of(
                Arguments.of(new PrincipalAuthenticationDetailsGrantedAuthority(userActor()), "sidecar-resolver-user.json"),
                Arguments.of(new DelegatedAuthenticationDetailsGrantedAuthority(userActor(), extensionActor()), "sidecar-resolver-delegated.json"),
                Arguments.of(new PrincipalAuthenticationDetailsGrantedAuthority(extensionActor()), "sidecar-resolver-extension-system.json")
        );
    }

    @ParameterizedTest
    @MethodSource
    void assertClaims(AuthenticationDetails authDetails, String fixturePath) {
        var resolver = new RuntimeSidecarJwtClaimsResolver();

        var claims = resolver.resolveAdditionalClaims(exchange(), authDetails).block();

        assertThat(claims).isNotNull();
        assertThat(claims.toJSONObject())
                .isEqualTo(ExpectedClaims.fromResource("/fixtures/expected-claims/" + fixturePath));
    }

    private static MockServerWebExchange exchange() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("https://foo.userapps.contentgrid.com").build());
        exchange.getAttributes().put(CONTENTGRID_APP_ID_ATTR, APPLICATION_ID);
        exchange.getAttributes().put(CONTENTGRID_DEPLOY_ID_ATTR, DEPLOYMENT_ID);
        return exchange;
    }
}
