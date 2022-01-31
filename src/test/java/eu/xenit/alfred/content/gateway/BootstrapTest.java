package eu.xenit.alfred.content.gateway;

import static com.jayway.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.oneOf;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
        "testing.bootstrap.users.1.authorities={\"employers\": [\"BE0999999999\", \"BE0123456789\"], \"customers\": [\"BE9988776655\", \"BE5544332211\", \"BE0987654321\"], \"singlevalue\": \"BE1234567890\"}",
        "opa.query=data.gateway.example.allow == true",
        "opa.service.url=http://opa:8181",
})
@AutoConfigureMockMvc
public class BootstrapTest {
    @Value("${local.server.port}")
    private int port;

    @BeforeEach
    void setupRestAssured() {
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Autowired
    private UserInfoController userInfoController;

    @Test
    public void testLogin() {

        given()
                .log().path()
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
        var result = userInfoController.userInfo(authentication);
        assertThat(result).extractingByKey("employers", InstanceOfAssertFactories.LIST)
                .containsExactlyInAnyOrder("BE0999999999", "BE0123456789");
        assertThat(result).extractingByKey("customers", InstanceOfAssertFactories.LIST)
                .containsExactlyInAnyOrder("BE9988776655", "BE5544332211", "BE0987654321");
        assertThat(result).extractingByKey("singlevalue", InstanceOfAssertFactories.STRING)
                .isEqualTo("BE1234567890");
    }

}
