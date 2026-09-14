package com.contentgrid.gateway.cors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;

class CorsConfigurationResolverTest {

    @Test
    void loadCorsFromHostHeader_hasAppliedDefaults() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("console.contentgrid.com"));

        var properties = new CorsResolverProperties();
        properties.getConfigurations().put("api.contentgrid.com", config);

        var resolver = new CorsConfigurationResolver(properties);

        var request = MockServerHttpRequest
                .get("http://api.contentgrid.com/me");
        var cors = resolver.getCorsConfiguration(MockServerWebExchange.from(request));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).singleElement().isEqualTo("console.contentgrid.com");
        assertThat(cors.getAllowedHeaders())
                .contains("Authorization", "Content-Type", "Last-Event-ID", "If-Match", "If-None-Match");
        assertThat(cors.getExposedHeaders()).contains("ETag");
        assertThat(cors.getAllowedMethods()).singleElement().isEqualTo("*");
        assertThat(cors.getMaxAge()).isEqualTo(1800L);
        assertThat(cors.getAllowCredentials()).isNull();
    }

}
