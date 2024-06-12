package com.contentgrid.gateway.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("contentgrid.gateway.runtime-platform")
public class RuntimePlatformProperties {

    private boolean enabled = false;

    private Map<String, EndpointConfiguration> endpoints = new HashMap<>();

    public Stream<EndpointDefinition> endpoints() {
        return endpoints.entrySet().stream().map(EndpointDefinition::new);
    }

    @Data
    static class EndpointConfiguration {
        private String uri;
        @NonNull
        private AuthorizationType authorization = AuthorizationType.DEFAULT;
    }

    public enum AuthorizationType {
        PUBLIC,
        AUTHENTICATED,
        DEFAULT
    }

    @Value
    @Accessors(fluent = true)
    @AllArgsConstructor
    public static class EndpointDefinition {
        private EndpointDefinition(Map.Entry<String, EndpointConfiguration> config) {
            this(
                    config.getKey(),
                    config.getValue().getUri(),
                    config.getValue().getAuthorization()
            );
        }

        String endpointId;
        String upstreamUri;
        AuthorizationType authorizationType;

        public String pathPattern() {
            return "/.contentgrid/"+endpointId+"/**";
        }
    }
}

