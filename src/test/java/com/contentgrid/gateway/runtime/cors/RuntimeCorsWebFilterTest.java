package com.contentgrid.gateway.runtime.cors;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_APP_ID_ATTR;
import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration.Keys;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationFragment;
import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.routing.DefaultRuntimeRequestResolver;
import java.util.List;
import java.util.Map;
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

    private ApplicationConfiguration appConfig;
    private RuntimeCorsConfigurationSource corsSource;

    @BeforeEach
    void setup() {
        appConfig = ApplicationConfigurationFragment.fromProperties(Map.of(
                Keys.CORS_ORIGINS, "https://frontend-domain.test"
        ));
        var appConfigRepo = new ComposableApplicationConfigurationRepository();
        appConfigRepo.merge(appConfig);
        corsSource = new RuntimeCorsConfigurationSource(new DefaultRuntimeRequestResolver(), appConfigRepo);
    }


    @Test
    void preflightRequest_allowed() {

        var request = MockServerHttpRequest.options("https://my-app.contentgrid.cloud/documents")
                .header(HttpHeaders.ORIGIN, "https://frontend-domain.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .build();
        var exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CONTENTGRID_APP_ID_ATTR, appConfig.getApplicationId());

        new CorsWebFilter(corsSource).filter(exchange, (_exchange) -> Mono.empty());

        var response = exchange.getResponse();

        assertThat(response.getHeaders())
                .containsEntry(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, List.of("https://frontend-domain.test"))
                .containsEntry(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, List.of(HttpMethod.POST.name()))
                .containsEntry(HttpHeaders.VARY, List.of(HttpHeaders.ORIGIN, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS));
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
    }

    @Test
    void preflightRequest_denied_badOrigin() {

        var request = MockServerHttpRequest.options("https://my-app.contentgrid.cloud/documents")
                .header(HttpHeaders.ORIGIN, "https://evil-domain.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .build();
        var exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CONTENTGRID_APP_ID_ATTR, appConfig.getApplicationId());

        new CorsWebFilter(corsSource).filter(exchange, (_exchange) -> Mono.empty());

        var response = exchange.getResponse();

        assertThat(response.getStatusCode())
                .isNotNull()
                .satisfies(status -> assertThat(status).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void corsRequest_denied_badOrigin() {

        var request = MockServerHttpRequest.post("https://my-app.contentgrid.cloud/documents")
                .header(HttpHeaders.ORIGIN, "https://evil-domain.test")
                .build();
        assertThat(CorsUtils.isPreFlightRequest(request)).isFalse();
        var exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CONTENTGRID_APP_ID_ATTR, appConfig.getApplicationId());

        new CorsWebFilter(corsSource).filter(exchange, (_exchange) -> Mono.empty());

        var response = exchange.getResponse();

        assertThat(response.getStatusCode())
                .isNotNull()
                .satisfies(status -> assertThat(status).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void corsRequest_denied_validOrigin() {

        var request = MockServerHttpRequest.post("https://my-app.contentgrid.cloud/documents")
                .header(HttpHeaders.ORIGIN, "https://frontend-domain.test")
                .build();
        assertThat(CorsUtils.isPreFlightRequest(request)).isFalse();
        var exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CONTENTGRID_APP_ID_ATTR, appConfig.getApplicationId());

        new CorsWebFilter(corsSource).filter(exchange, (_exchange) -> Mono.empty());

        var response = exchange.getResponse();

        // not rejected by CorsWebFilter
        assertThat(response.getStatusCode()).isNull();
        assertThat(response.getHeaders()).containsEntry(HttpHeaders.VARY,
                List.of(HttpHeaders.ORIGIN, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS));
    }
}
