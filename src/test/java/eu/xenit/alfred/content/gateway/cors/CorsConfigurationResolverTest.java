package eu.xenit.alfred.content.gateway.cors;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigurationResolverTest {

    @Test
    void loadCorsFromHostHeader_hasAppliedDefaults() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("console.content-cloud.eu"));

        var properties = new CorsResolverProperties();
        properties.getConfigurations().put("api.content-cloud.eu", config);

        var resolver = new CorsConfigurationResolver(properties);

        var request = MockServerHttpRequest
                .get("/me")
                .header("Host", "api.content-cloud.eu");
        var cors = resolver.getCorsConfiguration(MockServerWebExchange.from(request));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).singleElement().isEqualTo("console.content-cloud.eu");
        assertThat(cors.getAllowedHeaders()).contains("Authorization", "Content-Type");
        assertThat(cors.getAllowedMethods()).singleElement().isEqualTo("*");
        assertThat(cors.getMaxAge()).isEqualTo(1800L);
        assertThat(cors.getAllowCredentials()).isNull();
    }

}