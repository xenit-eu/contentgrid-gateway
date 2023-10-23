package com.contentgrid.gateway.runtime.cors;

import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.routing.ApplicationIdRequestResolver;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;

@RequiredArgsConstructor
public class RuntimeCorsConfigurationSource implements CorsConfigurationSource {

    @NonNull
    private final ApplicationIdRequestResolver applicationIdResolver;

    @NonNull
    private final ApplicationConfigurationRepository appConfigRepository;

    private final CorsConfigurationMapper corsConfigurationMapper = new RuntimeCorsConfigurationMapper();

    @Nullable
    @Override
    public CorsConfiguration getCorsConfiguration(@NonNull ServerWebExchange exchange) {
        return this.applicationIdResolver.resolveApplicationId(exchange)
                .flatMap(appId -> Optional.ofNullable(appConfigRepository.getApplicationConfiguration(appId)))
                .map(this.corsConfigurationMapper)
                .orElse(null);
    }
}
