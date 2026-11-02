package com.contentgrid.gateway.cors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import reactor.core.publisher.Mono;

class CorsConfigurationResolverTest {

    private CorsConfigurationResolver resolver;

    @BeforeEach
    void setup() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("console.contentgrid.com"));

        var properties = new CorsResolverProperties();
        properties.getConfigurations().put("api.contentgrid.com", config);

        resolver = new CorsConfigurationResolver(properties);
    }

    @Test
    void preflightRequest_hasAppliedDefaults() {
        var request = MockServerHttpRequest.options("https://api.contentgrid.com/orgs")
                .header(HttpHeaders.ORIGIN, "https://console.contentgrid.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .build();
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

    @Test
    void corsRequest_hasAppliedDefaults() {
        var request = MockServerHttpRequest
                .post("http://api.contentgrid.com/orgs")
                .build();
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

    @Test
    void preflightRequest_denied_unconfiguredHost() {
        var request = MockServerHttpRequest.options("https://other.contentgrid.com/me") // <-- no config, no default
                .header(HttpHeaders.ORIGIN, "https://console.contentgrid.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .build();

        assertRequestForbidden(resolver, request);
    }

    @Test
    void preflightRequest_denied_missingHost() {
        var request = MockServerHttpRequest.options("/me") // <-- no host
                .header(HttpHeaders.ORIGIN, "https://console.contentgrid.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .build();

        assertRequestForbidden(resolver, request);
    }

    @Test
    void preflightRequest_denied_badOrigin() {
        var request = MockServerHttpRequest.options("https://api.contentgrid.com/orgs")
                .header(HttpHeaders.ORIGIN, "https://evil-domain.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .build();

        assertRequestForbidden(resolver, request);
    }

    @Test
    void corsRequest_denied_badOrigin() {
        var request = MockServerHttpRequest.post("https://api.contentgrid.com/orgs")
                .header(HttpHeaders.ORIGIN, "https://evil-domain.test")
                .build();

        assertRequestForbidden(resolver, request);
    }

    private static void assertRequestForbidden(CorsConfigurationResolver resolver, MockServerHttpRequest request) {
        var exchange = MockServerWebExchange.from(request);
        new CorsWebFilter(resolver).filter(exchange, _exchange -> Mono.empty()).block();

        var response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
        assertThat(response.getHeaders().getAccessControlExposeHeaders()).isEmpty();
    }

}
