package eu.xenit.alfred.content.gateway;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "opa")
public class OpaProperties {
    private OpaServiceProperties service;
    private String query;

    @Data
    public static class OpaServiceProperties {
        private String url;
    }
}
