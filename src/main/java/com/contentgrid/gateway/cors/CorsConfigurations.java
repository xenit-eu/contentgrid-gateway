package com.contentgrid.gateway.cors;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.web.cors.CorsConfiguration;


@UtilityClass
public class CorsConfigurations {

    public static final List<String> DEFAULT_ALLOWED_METHODS = List.of("*");
    // The assistant uses text/event-streams to communicate. This header is needed to revisit a text event stream after a network connection reset
    public static final String LAST_EVENT_ID = "Last-Event-ID";
    public static final List<String> DEFAULT_ALLOWED_HEADERS = List.of(HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE, LAST_EVENT_ID, HttpHeaders.IF_MATCH, HttpHeaders.IF_NONE_MATCH);
    public static final List<String> DEFAULT_EXPOSED_HEADERS = List.of(HttpHeaders.ETAG);
    public static final Duration DEFAULT_MAX_AGE = Duration.of(30, ChronoUnit.MINUTES);

    public static CorsConfiguration applyDefaults(@Nullable CorsConfiguration cors) {
        if (cors == null) {
            return null;
        }

        cors = new CorsConfiguration(cors);
        if (cors.getAllowedMethods() == null) {
            cors.setAllowedMethods(DEFAULT_ALLOWED_METHODS);
        }

        if (cors.getMaxAge() == null) {
            cors.setMaxAge(DEFAULT_MAX_AGE);
        }

        if (cors.getAllowedHeaders() == null) {
            cors.setAllowedHeaders(DEFAULT_ALLOWED_HEADERS);
        }

        if (cors.getExposedHeaders() == null) {
            cors.setExposedHeaders(DEFAULT_EXPOSED_HEADERS);
        }

        return cors;
    }
}
