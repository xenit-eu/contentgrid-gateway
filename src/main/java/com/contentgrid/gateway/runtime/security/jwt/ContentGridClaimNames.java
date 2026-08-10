package com.contentgrid.gateway.runtime.security.jwt;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ContentGridClaimNames {

    /**
     * Contains encrypted claims of the principal in a delegated authentication token
     * @see <a href="https://github.com/xenit-eu/contentgrid-system-design/blob/main/architecture/specs/extension-authentication.md#additional-jwt-claims">Automation extension authentication spec</a>
     */
    public static final String RESTRICT_PRINCIPAL_CLAIMS = "restrict:principal_claims";

    /**
     * The application ID ({@link com.contentgrid.configuration.applications.ApplicationId}) for which the token is valid
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

    /**
     * Classification of the authentication that produced the token: {@code "user"}, {@code "system"} or
     * {@code "delegated"}. Mirrors {@link com.contentgrid.gateway.runtime.authorization.AuthenticationModel.AuthenticationKind}
     * and is embedded so the appserver's OPA sidecar doesn't need to re-derive it from issuer knowledge.
     * @see <a href="https://github.com/xenit-eu/contentgrid-system-design/blob/main/specs/abac-sidecar-opa.md">ABAC sidecar OPA spec</a>
     */
    public static final String AUTH_KIND = "contentgrid:auth:kind";

    /**
     * The already-processed principal claims (a {@value #ACTOR_KIND} member plus the principal's filtered
     * claims), embedded for sidecar apps because the appserver can neither resolve issuers nor decrypt
     * {@link #RESTRICT_PRINCIPAL_CLAIMS} itself.
     * @see <a href="https://github.com/xenit-eu/contentgrid-system-design/blob/main/specs/abac-sidecar-opa.md">ABAC sidecar OPA spec</a>
     */
    public static final String AUTH_PRINCIPAL = "contentgrid:auth:principal";

    /**
     * Member name used inside an {@link #ACT} object and inside {@link #AUTH_PRINCIPAL} to say whether that
     * actor/principal is a {@code "user"} or an {@code "extension"} (RFC 8693 §4.1 allows arbitrary identity
     * claims inside {@code act}).
     * @see <a href="https://github.com/xenit-eu/contentgrid-system-design/blob/main/architecture/specs/extension-authentication.md#gateway-extension">Automation extension authentication spec</a>
     */
    public static final String ACTOR_KIND = "kind";
}
