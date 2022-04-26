package eu.xenit.alfred.content.gateway.cors;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties("contentcloud.gateway.cors")
public class CorsResolverProperties {

    @Getter
    private final Map<String, CorsConfiguration> configurations = new LinkedHashMap<>();

}
