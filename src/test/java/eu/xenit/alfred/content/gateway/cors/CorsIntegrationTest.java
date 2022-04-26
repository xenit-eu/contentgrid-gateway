package eu.xenit.alfred.content.gateway.cors;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@Slf4j
@SpringBootTest(properties = {
        "content-cloud.gateway.cors.configurations.'[api.content-cloud.eu]'.allowedOrigins=https://console.content-cloud.eu",
        "content-cloud.gateway.cors.configurations.default.allowedOrigins=https://other-app.example"
})
class CorsIntegrationTest {

    @Autowired
    ApplicationContext context;

    WebTestClient http;

    @BeforeEach
    public void setup() {
        this.http = WebTestClient
                .bindToApplicationContext(this.context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }


    @Test
    public void corsPreflight_allowedOrigin() {
        var result = this.preflight("api.content-cloud.eu")
                .header("Origin", "https://console.content-cloud.eu")
                .exchange()
                .expectHeader().valueEquals("Access-Control-Allow-Headers", "authorization")
                .expectHeader().valueEquals("Access-Control-Allow-Methods", "GET")
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://console.content-cloud.eu")
                .expectHeader().doesNotExist("Access-Control-Allow-Credentials")
                .expectBody().isEmpty();

        log.info(result.toString());
    }

    @Test
    public void corsPreflight_fallback() {
        var result = this.preflight("other-service.content-cloud.eu")
                .header("Origin", "https://other-app.example")
                .exchange()
                .expectHeader().valueEquals("Access-Control-Allow-Headers", "authorization")
                .expectHeader().valueEquals("Access-Control-Allow-Methods", "GET")
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://other-app.example")
                .expectHeader().doesNotExist("Access-Control-Allow-Credentials")
                .expectBody().isEmpty();

        log.info(result.toString());
    }

    @Test
    public void corsPreflight_forbiddenOrigin() {
        this.preflight("api.content-cloud.eu")
                .header("Origin", "https://evil.overlord.example")
                .exchange()
                .expectStatus().isForbidden();

    }

    @Test
    public void corsPreflight_forbiddenSubdomain() {
        this.preflight("api.content-cloud.eu")
                .header("Origin", "https://evil.console.content-cloud.eu")
                .exchange().expectStatus().isForbidden();

        this.preflight("api.content-cloud.eu")
                .header("Origin", "https://evil.content-cloud.eu")
                .exchange().expectStatus().isForbidden();
    }

    private WebTestClient.RequestHeadersSpec<?> preflight(String hostname) {
        return this.http.options()
                .uri(uri -> uri.scheme("http").host("host-behind-proxy").port(8080).path("/me").build())
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization")
                .header("Host", hostname);

    }
}
