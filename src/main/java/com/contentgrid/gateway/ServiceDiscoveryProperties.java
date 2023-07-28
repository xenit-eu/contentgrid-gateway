package com.contentgrid.gateway;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "servicediscovery")
public class ServiceDiscoveryProperties {

    private boolean enabled = false;
    private String namespace = "default";
    private long resync = 60;
}
