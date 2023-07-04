package com.contentgrid.gateway.runtime.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface WebExchangePartitioner<P> extends Function<ServerWebExchange, Mono<P>> {

    static WebExchangePartitioner<String> byHostname() {
        return new PartitionByHostname();
    }

}

@Slf4j
final class PartitionByHostname implements WebExchangePartitioner<String> {

    @Override
    public Mono<String> apply(ServerWebExchange exchange) {
        return Mono.justOrEmpty(exchange.getRequest().getURI().getHost())
                .switchIfEmpty(Mono.just("unknown-host"))
                .doOnSuccess(partition -> {
                    var uri = exchange.getRequest().getURI().resolve("/");
                    log.debug("partition {} -> {}", uri, partition);
                });
    }
}
