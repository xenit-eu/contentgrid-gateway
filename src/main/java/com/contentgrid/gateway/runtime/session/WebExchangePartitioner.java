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

    private static final MessageDigest DIGEST;

    static {
        try {
            DIGEST = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Mono<String> apply(ServerWebExchange exchange) {
        return Mono.justOrEmpty(exchange.getRequest().getURI().getHost())
                .map(host -> host.getBytes(StandardCharsets.UTF_8))
                .map(DIGEST::digest)
                .map(HexFormat.of()::formatHex)
                .switchIfEmpty(Mono.just("unknown"))
                .doOnSuccess(partition -> {
                    var uri = exchange.getRequest().getURI().resolve("/");
                    log.debug("partition {} -> {}", uri, partition);
                });
    }
}
