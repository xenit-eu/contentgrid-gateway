package com.contentgrid.gateway.security;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import com.contentgrid.gateway.GatewayApplication;
import com.contentgrid.gateway.test.util.LoggingExchangeFilterFunction;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;


@Slf4j
@SpringBootTest(
    classes = GatewayApplication.class,
    properties = {
            "management.prometheus.metrics.export.enabled=true" /* default disabled by @SpringBootTest */
    })
public class GatewayApplicationActuatorSecurity {

    @Autowired
    ApplicationContext context;

    private WebTestClient webClient;

    private final SetRemoteAddressWebFilter remoteAddressWebFilter = new SetRemoteAddressWebFilter();

    @BeforeEach
    public void setup() {
        this.webClient = WebTestClient
                .bindToApplicationContext(this.context)
                // add Spring Security test Support
                .apply(springSecurity())
                .webFilter(remoteAddressWebFilter)

                .configureClient()
                .filter(new LoggingExchangeFilterFunction(log::info))

                .responseTimeout(Duration.ofHours(1))

                .build();
    }

    static Stream<Arguments> actuatorAccess() {
        return Stream.of(
                Arguments.of("/actuator", "127.0.0.1", 200),
                Arguments.of("/actuator", "8.8.8.8", 401),
                Arguments.of("/actuator", "100.64.0.1", 401),

                /* ALL ALLOWED */

                Arguments.of("/actuator/health", "127.0.0.1", 200),
                Arguments.of("/actuator/health", "8.8.8.8", 200),
                Arguments.of("/actuator/health", "100.64.0.1", 200),

                Arguments.of("/actuator/info", "127.0.0.1", 200),
                Arguments.of("/actuator/info", "8.8.8.8", 200),
                Arguments.of("/actuator/info", "100.64.0.1", 200),

                Arguments.of("/actuator/metrics", "127.0.0.1", 200),
                Arguments.of("/actuator/metrics", "8.8.8.8", 200),
                Arguments.of("/actuator/metrics", "100.64.0.1", 200),

                Arguments.of("/actuator/prometheus", "127.0.0.1", 200),
                Arguments.of("/actuator/prometheus", "8.8.8.8", 200),
                Arguments.of("/actuator/prometheus", "100.64.0.1", 200),

                /* LOCAL ALLOWED */

                Arguments.of("/actuator/configprops", "127.0.0.1", 200),
                Arguments.of("/actuator/configprops", "8.8.8.8", 401),
                Arguments.of("/actuator/configprops", "100.64.0.1", 401),

                Arguments.of("/actuator/env", "127.0.0.1", 200),
                Arguments.of("/actuator/env", "8.8.8.8", 401),
                Arguments.of("/actuator/env", "100.64.0.1", 401)
        );
    }

    @ParameterizedTest
    @MethodSource("actuatorAccess")
    void actuatorAccess(String endpoint, String remoteAddress, int httpStatusCode) {
        this.remoteAddressWebFilter.setRemoteAddress(remoteAddress);
        this.webClient
                .get()
                .uri(endpoint)
                .exchange()
                .expectStatus().isEqualTo(httpStatusCode);
    }

    static class SetRemoteAddressWebFilter implements WebFilter {

        @NonNull
        @Setter
        private Supplier<InetSocketAddress> remoteAddressSupplier = () -> null;

        public SetRemoteAddressWebFilter() {

        }

        public SetRemoteAddressWebFilter(String host, int port) {
            this.setRemoteAddress(host, port);
        }

        void setRemoteAddress(String host) {
            this.setRemoteAddress(host, 32400);
        }

        void setRemoteAddress(String host, int port) {
            this.setRemoteAddressSupplier(() -> new InetSocketAddress(host, port));
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            return chain.filter(decorate(exchange));
        }

        private ServerWebExchange decorate(ServerWebExchange exchange) {
            final ServerHttpRequest decorated = new ServerHttpRequestDecorator(exchange.getRequest()) {
                @Override
                public InetSocketAddress getRemoteAddress() {
                    return remoteAddressSupplier.get();
                }
            };

            return new ServerWebExchangeDecorator(exchange) {
                @Override
                public ServerHttpRequest getRequest() {
                    return decorated;
                }
            };
        }
    }

}
