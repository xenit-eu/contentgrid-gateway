package com.contentgrid.gateway.runtime.security.jwt;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ContentGridClaimNames {

    /**
     * Contains encrypted claims of the principal in a delegated authentication token
     * @see <a href="https://github.com/xenit-eu/contentgrid-system-design/blob/main/specs/automation-extension-authentication.md#additional-jwt-claims">Automation extension authentication spec</a>
     */
    public static final String RESTRICT_PRINCIPAL_CLAIMS = "restrict:principal_claims";

    /**
     * The application ID ({@link com.contentgrid.gateway.runtime.application.ApplicationId}) for which the token is valid
     * @see <a href="https://github.com/xenit-eu/contentgrid-system-design/blob/main/specs/automation-extension-authentication.md#additional-jwt-claims">Automation extension authentication spec</a>
     */
    public static final String CONTEXT_APPLICATION_ID = "context:application:id";

    /**
     * All domain names belonging to the application for which the token is valid
     * @see <a href="https://github.com/xenit-eu/contentgrid-system-design/blob/main/specs/automation-extension-authentication.md#additional-jwt-claims">Automation extension authentication spec</a>
     */
    public static final String CONTEXT_APPLICATION_DOMAINS = "context:application:domains";

    /**
     * Contains the claims of the actor in a delegated authentication token
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8693.html#name-act-actor-claim">RFC8693</a>
     */
    public static final String ACT = "act";
}
