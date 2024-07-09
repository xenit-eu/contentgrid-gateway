package com.contentgrid.gateway;

import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.authority.PrincipalAuthenticationDetailsGrantedAuthority;
import java.util.Map;
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

@SpringBootTest(properties = "contentgrid.gateway.user-info.enabled=true")
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
                        .authorities(new PrincipalAuthenticationDetailsGrantedAuthority(new Actor(
                                ActorType.USER,
                                () -> Map.of(
                                        "sub", "bb56a455-be04-4bc5-8b4b-b3a8761102e5",
                                        "name", "alice",
                                        "email", "alice@wonderland.example"
                                ),
                                null
                        ))))
                .get()
                .uri("/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.kind").isEqualTo("user")
                .jsonPath("$.principal.kind").isEqualTo("user")
                .jsonPath("$.principal.sub").isEqualTo("bb56a455-be04-4bc5-8b4b-b3a8761102e5")
                .jsonPath("$.principal.name").isEqualTo("alice")
                .jsonPath("$.principal.email").isEqualTo("alice@wonderland.example")
                .jsonPath("$.principal.claims").doesNotExist()
        ;


    }

    @Test
    public void withJwtPrincipal_expectHttp200_ok() {
        this.rest
                .mutateWith(mockJwt()
                        .authorities(new PrincipalAuthenticationDetailsGrantedAuthority(new Actor(
                                ActorType.USER,
                                () -> Map.of(
                                        "sub", "bb56a455-be04-4bc5-8b4b-b3a8761102e5",
                                        "name", "alice",
                                        "email", "alice@wonderland.example"
                                ),
                                null
                        ))))
                .get()
                .uri("/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.kind").isEqualTo("user")
                .jsonPath("$.principal.kind").isEqualTo("user")
                .jsonPath("$.principal.sub").isEqualTo("bb56a455-be04-4bc5-8b4b-b3a8761102e5")
                .jsonPath("$.principal.name").isEqualTo("alice")
                .jsonPath("$.principal.email").isEqualTo("alice@wonderland.example");
    }
}