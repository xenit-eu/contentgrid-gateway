package com.contentgrid.gateway.runtime.session;

import com.contentgrid.gateway.runtime.routing.RuntimeRequestResolver;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.DefaultWebSessionManager;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class RuntimeWebSessionManager extends DefaultWebSessionManager {

    private final RuntimeRequestResolver runtimeRequestResolver;

    private static final String APPID_SESSION_NAME = "com.contentgrid.gateway.runtime.application-id";

    @Override
    public Mono<WebSession> getSession(ServerWebExchange exchange) {
        return super.getSession(exchange).flatMap(session -> this.validateSession(session, exchange));
    }

    private Mono<WebSession> validateSession(@NonNull WebSession session, @NonNull ServerWebExchange exchange) {
        return runtimeRequestResolver.resolveApplicationId(exchange)
                .map(requestAppId -> {
                    // verify the session contains the same application-id
                    var sessionAppId = session.getAttributes().putIfAbsent(APPID_SESSION_NAME, requestAppId);
                    if (sessionAppId != null && !sessionAppId.equals(requestAppId)) {
                        log.warn("Request {} with SESSION {} has app-id:{}, but request is associated with app-id:{}",
                                exchange.getRequest().getURI(), session.getId(), sessionAppId, requestAppId);
                        return session.invalidate()
                                .then(Mono.fromRunnable(() -> log.warn("SESSION {} invalidated!", session.getId())))
                                .then(super.getSession(exchange))
                                .doOnNext(newSess -> newSess.getAttributes().put(APPID_SESSION_NAME, requestAppId));
                    }

                    // We end up here in 2 cases:
                    // 1. the session was already tagged with the correct app-id
                    // 2. this is a brand-new session, now tagged with the app-id
                    return Mono.just(session);

                })
                .orElse(Mono.defer(() -> {
                    // the exchange is NOT associated with an ApplicationId
                    // in that case, be on the safe side and invalidate the current session
                    session.invalidate();

                    log.warn("Invalidating SESSION {} because request was not associated with an ApplicationId",
                            session.getId());

                    return super.getSession(exchange);
                }));

    }
}
