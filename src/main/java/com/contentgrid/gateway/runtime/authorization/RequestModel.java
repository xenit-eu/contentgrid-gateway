package com.contentgrid.gateway.runtime.authorization;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.springframework.http.HttpHeaders;
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
                .headers(headersToMap(request.getHeaders()))
                .build();
    }

    private static Map<String, List<String>> headersToMap(HttpHeaders headers) {
        Map<String, List<String>> result = new HashMap<>();
        headers.forEach((key, value) -> result.put(key.toLowerCase(Locale.ROOT), value));
        return result;
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
