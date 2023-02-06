package com.contentgrid.gateway.filter.headers;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * {@link HttpHeadersFilter} that removes all Access-Control-* headers from downstream response.
 *
 * This is useful when CORS is fully handled by the Gateway and CORS responses from the downstream service,
 * should not be transferred to the gateway response.
 */
@Component
public class StripAccessControlHeadersFilter implements HttpHeadersFilter {

    @Override
    public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
        input.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
        input.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        input.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
        input.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        input.remove(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        input.remove(HttpHeaders.ACCESS_CONTROL_MAX_AGE);
        input.remove(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        input.remove(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);

        return input;
    }

    @Override
    public boolean supports(Type type) {
        return type.equals(Type.RESPONSE);
    }

}
