package eu.xenit.alfred.content.gateway.error;

import java.net.SocketException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.support.ServiceUnavailableException;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
public class ProxyUpstreamUnavailableWebFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Mono<Void> completion;
        try {
            completion = chain.filter(exchange);
        } catch(Exception e) {
            completion = Mono.error(e);
        }

        return completion.onErrorMap(SocketException.class, e -> {
            var newException = new ServiceUnavailableException(e.getMessage());
            newException.initCause(e);
            var request = exchange.getRequest();
            var routeName = exchange.getAttributeOrDefault(ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR, "null");
            log.error(String.format("Upstream service '%s' unavailable: '%s %s' failed", routeName, request.getMethod().name(), request.getURI().toASCIIString()), e);
            return newException;
        }).checkpoint();
    }

    @Override
    public int getOrder() {
        return NettyRoutingFilter.ORDER-1;
    }
}
