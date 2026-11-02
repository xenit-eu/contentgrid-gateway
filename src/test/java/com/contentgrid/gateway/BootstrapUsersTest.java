package com.contentgrid.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.authority.AuthenticationDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "testing.bootstrap.enable=true",
        "testing.bootstrap.users.0.username=alice",
        "testing.bootstrap.users.0.authorities={\"employers\": [\"BE0999999999\"]}",
        "testing.bootstrap.users.1.username=bob",
        "testing.bootstrap.users.1.authorities={\"employers\": [\"BE0999999999\", \"BE0123456789\"], \"customers\": [\"BE9988776655\", \"BE5544332211\", \"BE0987654321\"], \"singlevalue\": \"BE1234567890\"}"
})
@AutoConfigureWebTestClient
public class BootstrapUsersTest {

    @Autowired
    private WebTestClient http;

    @Test
    public void testLogin() {

        http.post()
                .uri("/login")
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData("username", "bob").with("password", "bob"))
                .exchange()
                .expectStatus().value(status -> assertThat(status).isIn(200, 302));
    }

    @Test
    @WithUserDetails("bob")
    public void testAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        var maybeAuthenticationDetails = authentication.getAuthorities()
                .stream()
                .filter(AuthenticationDetails.class::isInstance)
                .map(AuthenticationDetails.class::cast)
                .findFirst();

        assertThat(maybeAuthenticationDetails).hasValueSatisfying(authenticationDetails -> {
            assertThat(authenticationDetails.getPrincipal().getType()).isEqualTo(ActorType.USER);
            assertThat(authenticationDetails.getPrincipal().getClaims().getClaimAsStringList("employers"))
                    .containsExactlyInAnyOrder("BE0999999999", "BE0123456789");
            assertThat(authenticationDetails.getPrincipal().getClaims().getClaimAsStringList("customers"))
                    .containsExactlyInAnyOrder("BE9988776655", "BE5544332211", "BE0987654321");
            assertThat(authenticationDetails.getPrincipal().getClaims().getClaimAsString("singlevalue"))
                    .isEqualTo("BE1234567890");
            assertThat(authenticationDetails.getActor()).isNull();
        });
    }

}
