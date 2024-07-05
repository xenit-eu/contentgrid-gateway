package com.contentgrid.gateway.runtime.security.bearer;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("contentgrid.gateway.runtime-platform.external-issuers")
public class RuntimePlatformExternalIssuerProperties {

    private OidcIssuerProperties extensionSystem = new OidcIssuerProperties();

    @Data
    public static class OidcIssuerProperties {
        private String issuer;
        private String jwkSetUri;
        private List<String> jwsAlgorithms = List.of("RS256");
    }
}
