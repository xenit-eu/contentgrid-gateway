package com.contentgrid.gateway.runtime.session;

import static org.springframework.web.server.adapter.WebHttpHandlerBuilder.WEB_SESSION_MANAGER_BEAN_NAME;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.routing.RuntimeRequestResolver;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.InMemoryWebSessionStore;
import org.springframework.web.server.session.WebSessionIdResolver;
import org.springframework.web.server.session.WebSessionManager;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled")
@EnableConfigurationProperties({WebProperties.class, ServerProperties.class})
public class RuntimeWebSessionConfiguration {

    private final ServerProperties serverProperties;

    @Bean(name = WEB_SESSION_MANAGER_BEAN_NAME)
    public WebSessionManager runtimeWebSessionManager(ObjectProvider<WebSessionIdResolver> webSessionIdResolver,
            WebExchangePartitioner<?> partitioner) {
        return new PartitionedWebSessionManager<>(
                partitioner,
                (partition) -> {
                    log.debug("Creating new session manager for partition {}", partition);
                    var delegate = new DefaultWebSessionManager();
                    var timeout = this.serverProperties.getReactive().getSession().getTimeout();
                    delegate.setSessionStore(new MaxIdleTimeInMemoryWebSessionStore(timeout));
                    webSessionIdResolver.ifAvailable(delegate::setSessionIdResolver);
                    return delegate;
                });
    }

    @Bean
    WebExchangePartitioner<String> partitioner(RuntimeRequestResolver runtimeRequestResolver) {
        return exchange ->
                // partition by application-id
                Mono.justOrEmpty(runtimeRequestResolver.resolveApplicationId(exchange))
                .map(ApplicationId::toString)

                // fallback to hostname-based partioning
                .switchIfEmpty(WebExchangePartitioner.byHostname().apply(exchange));
    }

    static final class MaxIdleTimeInMemoryWebSessionStore extends InMemoryWebSessionStore {

        private final Duration timeout;

        private MaxIdleTimeInMemoryWebSessionStore(Duration timeout) {
            this.timeout = timeout;
        }

        @Override
        public Mono<WebSession> createWebSession() {
            return super.createWebSession().doOnSuccess(this::setMaxIdleTime);
        }

        private void setMaxIdleTime(WebSession session) {
            session.setMaxIdleTime(this.timeout);
        }

    }

}
