package com.contentgrid.gateway.runtime.authorization;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;

@Value
@Builder
public class RequestModel {
    String method;
    List<String> path;
    Map<String, List<String>> query;
    Map<String, List<String>> headers;

    public static RequestModel from(ServerHttpRequest request) {
        return RequestModel.builder()
                .method(request.getMethod().name())
                .path(List.of(uriToPathArray(request.getURI())))
                .query(request.getQueryParams())
                .headers(
                        request.getHeaders()
                                .entrySet()
                                .stream()
                                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT), Entry::getValue))
                )
                .build();
    }

    private static String[] uriToPathArray(URI uri) {
        uri = uri.normalize();

        var path = uri.getPath();
        if (path == null) {
            return new String[0];
        }

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (path.length() == 0) {
            return new String[0];
        }

        return path.split("/");
    }
}
