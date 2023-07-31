package com.contentgrid.gateway.runtime.cors;

import com.contentgrid.gateway.cors.CorsConfigurations;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import com.contentgrid.gateway.runtime.web.ContentGridRuntimeHeaders;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.web.cors.CorsConfiguration;

@RequiredArgsConstructor
class RuntimeCorsConfigurationMapper implements CorsConfigurationMapper {

    @Nullable
    private final List<String> allowedHeaders = List.of(
            HttpHeaders.AUTHORIZATION,
            ContentGridRuntimeHeaders.CONTENTGRID_APPLICATION_ID,
            ContentGridRuntimeHeaders.CONTENTGRID_DEPLOYMENT_ID
    );

    @Override
    public CorsConfiguration apply(ApplicationConfiguration applicationConfiguration) {
        var cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.copyOf(applicationConfiguration.getCorsOrigins()));
        cors.setAllowedHeaders(allowedHeaders);
        return CorsConfigurations.applyDefaults(cors);
    }

}
