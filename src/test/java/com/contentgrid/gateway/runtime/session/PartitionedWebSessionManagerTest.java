package com.contentgrid.gateway.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOAuth2Login;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import com.contentgrid.gateway.GatewayApplication;
import com.contentgrid.gateway.runtime.session.PartitionedWebSessionManagerTest.TestController;
import com.contentgrid.gateway.test.util.LoggingExchangeFilterFunction;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.WebSession;

@Slf4j
@SpringBootTest(
        classes = GatewayApplication.class,
        properties = {
                "contentgrid.gateway.runtime-platform.enabled=true"
        })
@Import(TestController.class)
class PartitionedWebSessionManagerTest {

    private static final String SESSION_COOKIE_NAME = "SESSION";

    @Controller
    static class TestController {

        @GetMapping("/test")
        @ResponseBody
        String test(WebSession session) {
            // explicitly start session to trigger session cookie
            session.start();

            return "OK";
        }
    }

    @Autowired
    ApplicationContext context;

    private WebTestClient client;

    @BeforeEach
    public void setup() {
        this.client = WebTestClient
                .bindToApplicationContext(this.context)
                .apply(springSecurity())
                .configureClient()
                .filter(new LoggingExchangeFilterFunction(log::info))
                .responseTimeout(Duration.ofHours(1))
                .build();
    }

    @Test
    void sessionCookie_attributes() {

        var sessionCookie = client
                .mutateWith(mockOAuth2Login())
                .get()
                .uri("https://app.userapps.contentegrid.app/test")
                .exchange()
                .expectStatus().isOk()
                .expectCookie().exists(SESSION_COOKIE_NAME)
                .returnResult(Void.class).getResponseCookies().getFirst(SESSION_COOKIE_NAME);

        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.getValue()).isNotEmpty();

        // session cookie should not be accessible to javascript
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        // only send session cookie over HTTPS
        assertThat(sessionCookie.isSecure()).isTrue();
        // Negative "Max-Age" means cookie is deleted when the browser session ends
        assertThat(sessionCookie.getMaxAge()).isNegative();
        // specifying "Domain" is less restrictive, because that also implies subdomains
        assertThat(sessionCookie.getDomain()).isNull();

        assertThat(sessionCookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void sessionCookie_resumingSession() {

        var sessionCookie = client
                .mutateWith(mockOAuth2Login())
                .get()
                .uri("https://app.userapps.contentegrid.app/test")
                .exchange()
                .expectStatus().isOk()
                .expectCookie().exists(SESSION_COOKIE_NAME)
                .returnResult(Void.class).getResponseCookies().getFirst(SESSION_COOKIE_NAME);

        assertThat(sessionCookie).isNotNull();

        // request including the session cookie to the same domain
        // should NOT rotate session id
        client
                .mutateWith(mockOAuth2Login())
                .get()
                .uri("https://app.userapps.contentegrid.app/test")
                .cookie("SESSION", sessionCookie.getValue())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Set-Cookie");
    }


    @Test
    void sessionCookie_differentDomains() {
        var sessionCookie = client
                .mutateWith(mockOAuth2Login())
                .get()
                .uri("https://app.userapps.contentegrid.app/test")
                .exchange()
                .expectStatus().isOk()
                .expectCookie().exists(SESSION_COOKIE_NAME)
                .returnResult(Void.class).getResponseCookies().getFirst(SESSION_COOKIE_NAME);

        assertThat(sessionCookie).isNotNull();

        // trying to re-use the session cookie for a different hostname
        client
                .mutateWith(mockOAuth2Login())
                .get()
                .uri("https://other.userapps.contentegrid.app/test")
                .cookie("SESSION", sessionCookie.getValue())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("Set-Cookie");
    }
}