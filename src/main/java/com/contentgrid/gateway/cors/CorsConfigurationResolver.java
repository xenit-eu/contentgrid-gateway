package com.contentgrid.gateway.cors;


import static com.contentgrid.gateway.cors.CorsConfigurations.applyDefaults;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.NonNull;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;

public class CorsConfigurationResolver implements CorsConfigurationSource {

    private final Map<String, CorsConfiguration> configurations = new LinkedHashMap<>();

    @NonNull
    private final CorsConfiguration fallback;



    public CorsConfigurationResolver(CorsResolverProperties properties) {
        properties.getConfigurations().forEach((host, cors) -> {
            if (!host.equalsIgnoreCase("default")) {
                this.configurations.put(host, applyDefaults(cors));
            }
        });

        this.fallback = applyDefaults(properties.getConfigurations().get("default"));
    }

    @Override
    public CorsConfiguration getCorsConfiguration(ServerWebExchange exchange) {
        var host = exchange.getRequest().getURI().getHost();
        if (host == null) {
            // The default CorsConfiguration denies all CORS requests
            return new CorsConfiguration();
        }

        return configurations.getOrDefault(host, this.fallback);
    }
}
