package com.contentgrid.gateway.runtime.cors;

import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.web.cors.CorsConfiguration;

class DefaultCorsConfigurationEventMapper implements CorsConfigurationMapper {

    static final List<String> DEFAULT_ALLOWED_METHODS = List.of("*");
    static final List<String> DEFAULT_ALLOWED_HEADERS = List.of("Authorization", "Content-Type");
    static final Duration DEFAULT_MAX_AGE = Duration.of(30, ChronoUnit.MINUTES);


    @Override
    public CorsConfiguration apply(ApplicationConfiguration applicationConfiguration) {
        var cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.copyOf(applicationConfiguration.getCorsOrigins()));
        return applyDefaults(cors);
    }

    private static CorsConfiguration applyDefaults(CorsConfiguration cors) {
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

        return cors;
    }
}
