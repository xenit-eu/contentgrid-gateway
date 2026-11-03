package com.contentgrid.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
@Tag("integration")
public class OpenPolicyAgentIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(OpenPolicyAgentIntegrationTest.class);

    @Container
    private static final GenericContainer<?> openPolicyAgent = new GenericContainer<>("docker.io/openpolicyagent/opa:1.20.1-debug")
            .withCopyFileToContainer(MountableFile.forClasspathResource("test.rego"), "/config/test.rego")
            .withExposedPorts(8181)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withCommand("run",
                    "--server",
                    "--log-format=json-pretty",
                    "--set=decision_logs.console=true",
                    "--addr=:8181",
                    "file:/config/test.rego");


    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String url = "http://" + openPolicyAgent.getHost() + ":" + openPolicyAgent.getMappedPort(8181);
        registry.add("opa.service.url", () -> url);
    }

    private static ResponseSpec tryGetAliceProfile(WebTestClient http) {
        ResponseCookie sessionCookie = http.post()
                .uri("/login")
                .body(BodyInserters.fromFormData("username", "alice").with("password", "alice"))
                .exchange()
                .returnResult(Void.class)
                .getResponseCookies()
                .getFirst("SESSION");
        assertThat(sessionCookie).isNotNull();

        return http.get()
                .uri("/me")
                .accept(MediaType.APPLICATION_JSON)
                .cookie("SESSION", sessionCookie.getValue())
                .exchange();
    }

    @Nested
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
            "contentgrid.gateway.user-info.enabled=true",
            "testing.bootstrap.enable=true",
            "testing.bootstrap.users.0.username=alice",
            "testing.bootstrap.users.0.authorities={\"employers\": [\"BE0999999999\"]}",
            // "gateway.example" matches the package name
            "opa.query=data.gateway.example.allow == true",
    })
    @AutoConfigureWebTestClient
    public class HappyPathTest {
        @Autowired
        private WebTestClient http;

        @Test
        public void testConfiguredOpaQueryHappyPath() {
            tryGetAliceProfile(http)
                .expectStatus().isOk()
                .expectBody().jsonPath("$.principal.name").isEqualTo("alice");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
            "contentgrid.gateway.user-info.enabled=true",
            "testing.bootstrap.enable=true",
            "testing.bootstrap.users.0.username=alice",
            "testing.bootstrap.users.0.authorities={\"employers\": [\"BE0999999999\"]}",
            // "non.matching" does not match the package name
            "opa.query=data.gateway.non.matching.allow == true",
    })
    @AutoConfigureWebTestClient
    public class UnhappyPathTest {
        @Autowired
        private WebTestClient http;

        @Test
        public void testConfiguredOpaQueryUnhappyPath() {
            tryGetAliceProfile(http)
                .expectStatus().isForbidden();
        }
    }
}
