package com.contentgrid.gateway.security.opa;

import com.contentgrid.thunx.pdp.opa.OpaInputProvider;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;

public class ContentgridOpaInputProvider implements OpaInputProvider<Authentication, ServerWebExchange> {

    @Override
    public Map<String, Object> createInput(Authentication authenticationContext, ServerWebExchange requestContext) {
        var request = RequestModel.from(requestContext.getRequest());
        var auth = AuthenticationModel.from(authenticationContext);
        return Map.of(
                "auth", auth,
                "request", request,

                // Deprecated input properties, for backwards compatibility with existing
                // applications only
                "method", request.getMethod(),
                "path", request.getPath(),
                "queryParams", request.getQuery(),
                "user", auth.getPrincipal()
        );
    }
}
