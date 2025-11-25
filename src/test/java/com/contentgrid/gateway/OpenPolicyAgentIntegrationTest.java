package com.contentgrid.gateway;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.matchesRegex;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
    private static final GenericContainer<?> openPolicyAgent = new GenericContainer<>("docker.io/openpolicyagent/opa:1.10.1-debug")
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

    @BeforeEach
    void setupRestAssured() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private static ValidatableResponse tryGetAliceProfile(int port) {
        RestAssured.port = port;

        String sessionCookie = given()
                .body("username=alice&password=alice")
                .contentType(ContentType.URLENC)
                .post("/login")
                .then()
                .extract().cookie("SESSION");

        return given()
                .accept(ContentType.JSON)
                .cookie("SESSION", sessionCookie)
                .get("/me")
                .then()
                .log().body();
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
    public class HappyPathTest {
        @Value("${local.server.port}")
        private int port;

        @Test
        public void testConfiguredOpaQueryHappyPath() {
            tryGetAliceProfile(port)
                .assertThat().statusCode(200)
                .and()
                .assertThat().body(matchesRegex(".*\"name\":\\s*\"alice\".*"));
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
    public class UnhappyPathTest {
        @Value("${local.server.port}")
        private int port;

        @Test
        public void testConfiguredOpaQueryUnhappyPath() {
            tryGetAliceProfile(port)
                .assertThat().statusCode(403);
        }
    }
}
