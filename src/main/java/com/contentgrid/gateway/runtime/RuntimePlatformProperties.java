package com.contentgrid.gateway.runtime;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("contentgrid.gateway.runtime-platform")
public class RuntimePlatformProperties {

    private boolean enabled = false;

    private Map<String, EndpointConfiguration> endpoints = new HashMap<>();

    @Data
    static class EndpointConfiguration {
        private String uri;
    }
}

