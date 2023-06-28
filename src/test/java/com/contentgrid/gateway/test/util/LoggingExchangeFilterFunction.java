package com.contentgrid.gateway.test.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class LoggingExchangeFilterFunction implements ExchangeFilterFunction {

    @NonNull
    private final Consumer<String> logger;

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {

        var sb = new StringBuilder("\n");

        sb.append("> Request: ").append(request.method()).append(" ").append(request.url()).append("\n");
        sb.append("> Headers:\n");

        request.headers().forEach((name, values) ->
                sb.append(">\t%s: %s\n".formatted(name, String.join("; ", values))));

        var originalBodyInserter = request.body();
        var loggingClientRequest = ClientRequest.from(request)
                .body((outputMessage, context) -> {
                    var loggingOutputMessage = new ClientHttpRequestDecorator(outputMessage) {
                        private final AtomicBoolean alreadyLogged = new AtomicBoolean(false);

                        @Override
                        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                            boolean needToLog = alreadyLogged.compareAndSet(false, true);
                            if (needToLog) {
                                sb.append("> Body:\n");
                                body = DataBufferUtils.join(body).doOnNext(content -> {
                                    sb.append(content.toString(StandardCharsets.UTF_8));
                                });
                                sb.append("\n");
                            }
                            return super.writeWith(body);
                        }

                        @Override
                        public Mono<Void> setComplete() { // This is for requests with no body (e.g. GET).
                            boolean needToLog = alreadyLogged.compareAndSet(false, true);
                            if (needToLog) {
                                sb.append("> Body: <empty>\n");
                            }
                            return super.setComplete();
                        }
                    };

                    return originalBodyInserter.insert(loggingOutputMessage, context);
                })
                .build();

        return next.exchange(loggingClientRequest)
                .map(response -> {
                    sb.append("\n");
                    sb.append("< ").append(response.statusCode()).append("\n");
                    sb.append("< Headers:\n");

                    response.headers().asHttpHeaders().forEach((key, value) ->
                            sb.append("< \t%s: %s\n".formatted(key, String.join("; ", value))));

                    return response.mutate()
                            .body(flux -> {

                                sb.append("< Body: ");
                                var cache = flux.cache();

                                DataBufferUtils.join(cache).subscribe(data -> {
                                    var contentType = response.headers().contentType().orElse(null);
                                    if (isJsonCompatible(contentType)) {
                                        var mapper = new ObjectMapper();

                                        var content = data.toString(StandardCharsets.UTF_8);
                                        try {
                                            var prettyJson = mapper.readTree(content).toPrettyString();
                                            sb.append("\n").append(prettyJson);
                                        } catch (JsonProcessingException e) {
                                            sb.append("<failed json decoding: %s>".formatted(content));
                                        }
                                    } else {
                                        sb.append("<content-type ").append(contentType).append(" - ")
                                                .append(data.readableByteCount()).append(" bytes not decoded>\n");
                                    }
                                });

                                return cache;
                            })
                            .build();
                })
                .doOnNext(response -> logger.accept(sb.toString()));
    }

    private static final List<MediaType> JSON_MEDIATYPES = List.of(
            MediaType.APPLICATION_JSON,
            new MediaType("application", "*+json")
    );

    private static boolean isJsonCompatible(@Nullable MediaType contentType) {
        if (contentType == null) {
            return false;
        }

        return JSON_MEDIATYPES.stream().anyMatch(jsonType -> jsonType.includes(contentType));
    }
}
