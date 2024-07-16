package com.contentgrid.gateway.security.jwt.issuer.jwk.source;

import com.nimbusds.jose.jwk.source.CachingJWKSetSource;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.jwk.source.OutageTolerantJWKSetSource;
import com.nimbusds.jose.jwk.source.RefreshAheadCachingJWKSetSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.events.Event;
import com.nimbusds.jose.util.events.EventListener;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingJWKSetSourceEventListener<S extends JWKSetSource<C>, C extends SecurityContext> implements EventListener<S, C> {

    @Override
    public void notify(Event<S, C> event) {
        if(event instanceof RefreshAheadCachingJWKSetSource.ScheduledRefreshFailed<?> scheduledRefreshFailedEvent) {
            log.error("Failed to refresh JWKSet {}", scheduledRefreshFailedEvent.getSource(),
                    scheduledRefreshFailedEvent.getException());
        } else if(event instanceof CachingJWKSetSource.UnableToRefreshEvent<?> unableToRefreshEvent) {
            log.error("Failed to refresh JWKSet {}", unableToRefreshEvent.getSource());
        } else if(event instanceof CachingJWKSetSource.RefreshInitiatedEvent<?> refreshInitiatedEvent) {
            log.debug("Starting refresh of JWKSet {}", refreshInitiatedEvent.getSource());
        } else if(event instanceof CachingJWKSetSource.RefreshCompletedEvent<?> refreshCompletedEvent) {
            log.debug("JWKSet {} was refreshed", refreshCompletedEvent.getSource());
        } else if(event instanceof OutageTolerantJWKSetSource.OutageEvent<?> outageEvent) {
            log.warn("JWKSet {} is unavailable; fallback remains available for {}", outageEvent.getSource(), Duration.ofMillis(outageEvent.getRemainingTime()), outageEvent.getException());
        }

    }
}
