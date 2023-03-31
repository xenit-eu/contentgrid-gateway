package com.contentgrid.gateway.cors;


import static com.contentgrid.gateway.cors.CorsConfigurations.applyDefaults;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;

public class CorsConfigurationResolver implements CorsConfigurationSource {

    private final Map<String, CorsConfiguration> configurations = new LinkedHashMap<>();

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
        var host =  exchange.getRequest().getHeaders().getHost();
        if (host == null) {
            return null;
        }

        var hostname = host.getHostName();
        return configurations.getOrDefault(hostname, this.fallback);
    }
}
