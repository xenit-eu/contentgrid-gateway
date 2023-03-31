package com.contentgrid.gateway.runtime.cors;

import com.contentgrid.gateway.cors.CorsConfigurations;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import java.util.List;
import org.springframework.web.cors.CorsConfiguration;

class DefaultCorsConfigurationMapper implements CorsConfigurationMapper {

    @Override
    public CorsConfiguration apply(ApplicationConfiguration applicationConfiguration) {
        var cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.copyOf(applicationConfiguration.getCorsOrigins()));
        return CorsConfigurations.applyDefaults(cors);
    }

}
