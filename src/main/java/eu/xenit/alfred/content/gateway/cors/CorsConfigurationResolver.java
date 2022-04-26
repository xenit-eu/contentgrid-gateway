package eu.xenit.alfred.content.gateway.cors;


import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CorsConfigurationResolver implements CorsConfigurationSource {

    private final Map<String, CorsConfiguration> configurations = new LinkedHashMap<>();

    private final CorsConfiguration fallback;

    public static final List<String> DEFAULT_ALLOWED_METHODS = List.of("*");
    public static final List<String> DEFAULT_ALLOWED_HEADERS = List.of("Authorization", "Content-Type");
    public static final Duration DEFAULT_MAX_AGE = Duration.of(30, ChronoUnit.MINUTES);


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
