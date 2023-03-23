package com.contentgrid.gateway.runtime.session;

import static org.springframework.web.server.adapter.WebHttpHandlerBuilder.WEB_SESSION_MANAGER_BEAN_NAME;

import com.contentgrid.gateway.runtime.routing.RuntimeRequestResolver;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.InMemoryWebSessionStore;
import org.springframework.web.server.session.WebSessionIdResolver;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled")
@EnableConfigurationProperties({WebProperties.class, ServerProperties.class})
public class RuntimeWebSessionConfiguration {

    private final ServerProperties serverProperties;


    @Bean(name = WEB_SESSION_MANAGER_BEAN_NAME)
    public RuntimeWebSessionManager runtimeWebSessionManager(RuntimeRequestResolver runtimeRequestResolver,
            ObjectProvider<WebSessionIdResolver> webSessionIdResolver) {
        var webSessionManager = new RuntimeWebSessionManager(runtimeRequestResolver);

        // See WebFluxAutoConfiguration
        var timeout = this.serverProperties.getReactive().getSession().getTimeout();
        webSessionManager.setSessionStore(new MaxIdleTimeInMemoryWebSessionStore(timeout));
        webSessionIdResolver.ifAvailable(webSessionManager::setSessionIdResolver);

        return webSessionManager;
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
