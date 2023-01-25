package eu.xenit.alfred.content.gateway.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("contentgrid.gateway.runtime-platform")
public class RuntimePlatformProperties {

    private boolean enabled = false;

}
