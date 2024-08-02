package com.contentgrid.gateway.runtime.cors;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_APP_ID_ATTR;
import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.configuration.applications.ApplicationConfiguration;
import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.config.StaticApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.routing.CachingApplicationIdRequestResolver;
import com.contentgrid.gateway.runtime.web.ContentGridRuntimeHeaders;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.cors.reactive.CorsWebFilter;
import reactor.core.publisher.Mono;

class RuntimeCorsWebFilterTest {

    private ApplicationId applicationId = ApplicationId.random();
    private RuntimeCorsConfigurationSource corsSource;

    @BeforeEach
    void setup() {
        var appConfigRepo = new StaticApplicationConfigurationRepository(Map.of(
                applicationId,
                ApplicationConfiguration.builder()
                        .corsOrigin("https://frontend-domain.test")
                        .build()
        ));

        var appIdResolver = new CachingApplicationIdRequestResolver(exchange -> Optional.empty());
        corsSource = new RuntimeCorsConfigurationSource(appIdResolver, appConfigRepo);
    }


    @Test
    void preflightRequest_allowed() {

        var request = MockServerHttpRequest.options("https://my-app.contentgrid.cloud/documents")
                .header(HttpHeaders.ORIGIN, "https://frontend-domain.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION)
                .build();
        var exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CONTENTGRID_APP_ID_ATTR, applicationId);

        new CorsWebFilter(corsSource).filter(exchange, (_exchange) -> Mono.empty());

        var response = exchange.getResponse();
        var headers = response.getHeaders();

        assertThat(headers.getAccessControlAllowOrigin()).isEqualTo("https://frontend-domain.test");
        assertThat(headers.getAccessControlAllowMethods()).contains(HttpMethod.POST);
        assertThat(headers.getAccessControlAllowHeaders()).contains(HttpHeaders.AUTHORIZATION);
        assertThat(headers.getVary()).containsExactly(
                        HttpHeaders.ORIGIN,
                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);

        // We do NOT allow CORS requests using cookies, authorization headers or TLS client certificates
        assertThat(headers.getAccessControlAllowCredentials()).isFalse();
    }

    @Test
    void preflightRequest_denied_missingCorsConfig() {

        var request = MockServerHttpRequest.options("https://my-app.contentgrid.cloud/documents")
                .header(HttpHeaders.ORIGIN, "https://frontend-domain.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .build();
        var exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CONTENTGRID_APP_ID_ATTR, ApplicationId.random()); // <-- CorsConfig lookup fails

        new CorsWebFilter(corsSource).filter(exchange, (_exchange) -> Mono.empty());

        var response = exchange.getResponse();

        assertThat(response.getStatusCode())
                .isNotNull()
                .satisfies(status -> assertThat(status).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(response.getHeaders().getAccessControlExposeHeaders()).isEmpty();
    }

    @Test
    void preflightRequest_denied_badOrigin() {

        var request = MockServerHttpRequest.options("https://my-app.contentgrid.cloud/documents")
                .header(HttpHeaders.ORIGIN, "https://evil-domain.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .build();
        var exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CONTENTGRID_APP_ID_ATTR, applicationId);

        new CorsWebFilter(corsSource).filter(exchange, (_exchange) -> Mono.empty());

        var response = exchange.getResponse();

        assertThat(response.getStatusCode())
                .isNotNull()
                .satisfies(status -> assertThat(status).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(response.getHeaders().getAccessControlExposeHeaders()).isEmpty();
    }

    @Test
    void corsRequest_denied_badOrigin() {

        var request = MockServerHttpRequest.post("https://my-app.contentgrid.cloud/documents")
                .header(HttpHeaders.ORIGIN, "https://evil-domain.test")
                .build();
        assertThat(CorsUtils.isPreFlightRequest(request)).isFalse();
        var exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CONTENTGRID_APP_ID_ATTR, applicationId);

        new CorsWebFilter(corsSource).filter(exchange, (_exchange) -> Mono.empty());

        var response = exchange.getResponse();

        assertThat(response.getStatusCode())
                .isNotNull()
                .satisfies(status -> assertThat(status).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(response.getHeaders().getAccessControlExposeHeaders()).isEmpty();
    }

    @Test
    void corsRequest_allowed_validOrigin() {

        var request = MockServerHttpRequest.post("https://my-app.contentgrid.cloud/documents")
                .header(HttpHeaders.ORIGIN, "https://frontend-domain.test")
                .build();
        assertThat(CorsUtils.isPreFlightRequest(request)).isFalse();
        var exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CONTENTGRID_APP_ID_ATTR, applicationId);

        new CorsWebFilter(corsSource).filter(exchange, (_exchange) -> Mono.empty());

        var response = exchange.getResponse();

        // not rejected by CorsWebFilter
        assertThat(response.getStatusCode()).isNull();
        assertThat(response.getHeaders()).containsEntry(HttpHeaders.VARY,
                List.of(HttpHeaders.ORIGIN, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS));
        assertThat(response.getHeaders().getAccessControlExposeHeaders())
                .containsExactlyInAnyOrder(
                        ContentGridRuntimeHeaders.CONTENTGRID_APPLICATION_ID,
                        ContentGridRuntimeHeaders.CONTENTGRID_DEPLOYMENT_ID,
                        HttpHeaders.CONTENT_DISPOSITION);
    }
}
