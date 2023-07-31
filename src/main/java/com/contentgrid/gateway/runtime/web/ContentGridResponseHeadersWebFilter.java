package com.contentgrid.gateway.runtime.web;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
public class ContentGridResponseHeadersWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        copyAttributeToResponseHeader(exchange,
                ContentGridAppRequestWebFilter.CONTENTGRID_APP_ID_ATTR,
                ContentGridRuntimeHeaders.CONTENTGRID_APPLICATION_ID);
        copyAttributeToResponseHeader(exchange,
                ContentGridAppRequestWebFilter.CONTENTGRID_DEPLOY_ID_ATTR,
                ContentGridRuntimeHeaders.CONTENTGRID_DEPLOYMENT_ID);

        return chain.filter(exchange);
    }

    private static void copyAttributeToResponseHeader(ServerWebExchange exchange, String attributeName, String headerName) {
        var attribute = exchange.getAttribute(attributeName);
        if (attribute != null) {
            exchange.getResponse().getHeaders().put(headerName, List.of(attribute.toString()));
        }
    }
}
