package eu.xenit.alfred.content.gateway.filter.factory;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class LogRequestHeaderGatewayFilterFactory
        extends AbstractGatewayFilterFactory<AbstractGatewayFilterFactory.NameConfig> {

    public LogRequestHeaderGatewayFilterFactory() {
        super(NameConfig.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList(NAME_KEY);
    }

    @Override
    public GatewayFilter apply(NameConfig config) {
        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                final HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
                final List<String> headerValues = requestHeaders.getOrEmpty(config.getName());

                log.info("{} {} - {}: {}", exchange.getRequest().getMethod().name(), exchange.getRequest().getPath().toString(), config.getName(), String.join(",", headerValues));

                return chain.filter(exchange);
            }

            @Override
            public String toString() {
                return filterToStringCreator(LogRequestHeaderGatewayFilterFactory.this)
                        .append("name", config.getName()).toString();
            }
        };
    }

}
