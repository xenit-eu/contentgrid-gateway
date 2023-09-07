package com.contentgrid.gateway.runtime.cors;

import com.contentgrid.gateway.cors.CorsConfigurations;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import com.contentgrid.gateway.runtime.web.ContentGridRuntimeHeaders;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;

@RequiredArgsConstructor
class RuntimeCorsConfigurationMapper implements CorsConfigurationMapper {

    private final List<String> exposedHeaders = List.of(
            ContentGridRuntimeHeaders.CONTENTGRID_APPLICATION_ID,
            ContentGridRuntimeHeaders.CONTENTGRID_DEPLOYMENT_ID,

            HttpHeaders.CONTENT_DISPOSITION
    );

    @Override
    public CorsConfiguration apply(ApplicationConfiguration applicationConfiguration) {
        var cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.copyOf(applicationConfiguration.getCorsOrigins()));
        cors.setExposedHeaders(exposedHeaders);
        return CorsConfigurations.applyDefaults(cors);
    }

}
