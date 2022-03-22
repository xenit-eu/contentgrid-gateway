package eu.xenit.alfred.content.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest
class UserInfoControllerTest {

    @Autowired
    ApplicationContext context;

    WebTestClient rest;

    @BeforeEach
    public void setup() {
        this.rest = WebTestClient
                .bindToApplicationContext(this.context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    @Test
    public void whenNotAuthenticated_expectHttp401_unauthorized() {
        this.rest
                .get()
                .uri("/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    public void withOidcPrincipal_expectHttp200_ok() {
        this.rest
                .mutateWith(mockOidcLogin()
                        .idToken(idToken -> idToken
                                .subject("bb56a455-be04-4bc5-8b4b-b3a8761102e5")
                                .claim("name", "alice")
                                .claim("email", "alice@wonderland.example")))
                .get()
                .uri("/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sub").isEqualTo("bb56a455-be04-4bc5-8b4b-b3a8761102e5")
                .jsonPath("$.name").isEqualTo("alice")
                .jsonPath("$.email").isEqualTo("alice@wonderland.example");

    }

    @Test
    public void withJwtPrincipal_expectHttp200_ok() {
        this.rest
                .mutateWith(mockJwt()
                        .jwt(jwt -> jwt
                                .subject("bb56a455-be04-4bc5-8b4b-b3a8761102e5")
                                .claim("name", "alice")
                                .claim("email", "alice@wonderland.example")))
                .get()
                .uri("/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sub").isEqualTo("bb56a455-be04-4bc5-8b4b-b3a8761102e5")
                .jsonPath("$.name").isEqualTo("alice")
                .jsonPath("$.email").isEqualTo("alice@wonderland.example");
    }
}