package eu.xenit.alfred.content.gateway;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "servicediscovery")
public class ServiceDiscoveryProperties {
    private boolean enabled = false;
    private String namespace;
}
