package com.contentgrid.gateway.cors;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;

@ConfigurationProperties("contentgrid.gateway.cors")
public class CorsResolverProperties {

    @Getter
    private final Map<String, CorsConfiguration> configurations = new LinkedHashMap<>();

}
