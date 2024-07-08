package com.contentgrid.gateway;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.oneOf;

import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.authority.AuthenticationDetails;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithUserDetails;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "testing.bootstrap.enable=true",
        "testing.bootstrap.users.0.username=alice",
        "testing.bootstrap.users.0.authorities={\"employers\": [\"BE0999999999\"]}",
        "testing.bootstrap.users.1.username=bob",
        "testing.bootstrap.users.1.authorities={\"employers\": [\"BE0999999999\", \"BE0123456789\"], \"customers\": [\"BE9988776655\", \"BE5544332211\", \"BE0987654321\"], \"singlevalue\": \"BE1234567890\"}"
})
@AutoConfigureMockMvc
public class BootstrapUsersTest {

    @Value("${local.server.port}")
    private int port;

    @BeforeEach
    void setupRestAssured() {
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    public void testLogin() {

        given()
                .accept(ContentType.JSON)
                .body("username=bob&password=bob")
                .contentType(ContentType.URLENC)
                .post("/login")
                .then()
                .log().body()
                .assertThat()
                .statusCode(oneOf(200, 302));
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
