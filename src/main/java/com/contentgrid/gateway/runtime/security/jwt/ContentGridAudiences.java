package com.contentgrid.gateway.runtime.security.jwt;

import com.contentgrid.configuration.applications.ApplicationId;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ContentGridAudiences {

    /**
     * Audience for the 'authentication' endpoint
     * @see <a href="https://github.com/xenit-eu/contentgrid-system-design/blob/main/specs/automation-extension-authentication.md#client-facing-token-exchange">Automation extension authentication spec</a>
     */
    public static final String SYSTEM_ENDPOINT_AUTHENTICATION = systemEndpoint("authentication");

    public static String systemEndpoint(String endpointId) {
        return "contentgrid:system:endpoints:"+endpointId;
    }

    /**
     * Audience for an application
     *
     * @see <a href="https://github.com/xenit-eu/contentgrid-system-design/blob/main/specs/automation-extension-authentication.md#gateway-extension">Automation extension authentication spec</a>
     */
    public static String application(ApplicationId applicationId) {
        return "contentgrid:application:"+applicationId.getValue();
    }

}
