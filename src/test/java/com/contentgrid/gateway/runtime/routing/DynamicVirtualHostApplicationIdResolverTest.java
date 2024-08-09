package com.contentgrid.gateway.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.configuration.api.ComposedConfiguration;
import com.contentgrid.configuration.api.observable.Observable.UpdateType;
import com.contentgrid.configuration.api.observable.Publisher;
import com.contentgrid.configuration.applications.ApplicationConfiguration;
import com.contentgrid.configuration.applications.ApplicationId;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
class DynamicVirtualHostApplicationIdResolverTest {

    @Test
    void resolveDomains() {
        var publisher = new Publisher<ComposedConfiguration<ApplicationId, ApplicationConfiguration>>();
        publisher.observe().doOnNext(event -> log.info("event: {}", event)).subscribe();
        var resolver = new DynamicVirtualHostApplicationIdResolver(publisher);
        var appId = ApplicationId.random();

        // verify nothing gets resolved
        assertThat(resolver.resolveApplicationId(exchange("https://primary.contentgrid.test/foo"))).isEmpty();
        assertThat(resolver.resolveApplicationId(exchange("https://secondary.domain.test/foo"))).isEmpty();
        assertThat(resolver.resolveApplicationId(exchange("https://alternative.domain.test/foo"))).isEmpty();

        // add primary and secondary domain
        publisher.emit(UpdateType.ADD, ApplicationConfiguration.builder()
                .routingDomain("primary.contentgrid.test")
                .routingDomain("secondary.domain.test")
                .buildForApplication(appId)
        );
        assertThat(resolver.resolveApplicationId(exchange("https://primary.contentgrid.test/foo"))).hasValue(appId);
        assertThat(resolver.resolveApplicationId(exchange("https://secondary.domain.test/foo"))).hasValue(appId);
        assertThat(resolver.resolveApplicationId(exchange("https://alternative.domain.test/foo"))).isEmpty();

        // remove primary domain, add alternative domain
        publisher.emit(UpdateType.UPDATE, ApplicationConfiguration.builder()
                .routingDomain("alternative.domain.test")
                .routingDomain("secondary.domain.test")
                .buildForApplication(appId)
        );
        assertThat(resolver.resolveApplicationId(exchange("https://primary.contentgrid.test/foo"))).isEmpty();
        assertThat(resolver.resolveApplicationId(exchange("https://secondary.domain.test/foo"))).hasValue(appId);
        assertThat(resolver.resolveApplicationId(exchange("https://alternative.domain.test/foo"))).hasValue(appId);
    }

    @Test
    void hijackDomain() {
        var publisher = new Publisher<ComposedConfiguration<ApplicationId, ApplicationConfiguration>>();
        publisher.observe().doOnNext(event -> log.info("event: {}", event)).subscribe();
        var resolver = new DynamicVirtualHostApplicationIdResolver(publisher);

        var app1 = ApplicationId.random();
        publisher.emit(UpdateType.ADD, ApplicationConfiguration.builder()
                .routingDomain("my.domain.test")
                .buildForApplication(app1)
        );
        assertThat(resolver.resolveApplicationId(exchange("https://my.domain.test/foo"))).hasValue(app1);

        // app2 tries to hijack the domain
        var app2 = ApplicationId.random();
        publisher.emit(UpdateType.ADD, ApplicationConfiguration.builder()
                .routingDomain("my.domain.test")
                .buildForApplication(app2)
        );

        // err on the side of caution - don't route any domains in case of a conflict
        assertThat(resolver.resolveApplicationId(exchange("https://my.domain.test/foo"))).isEmpty();

    }

    @Test
    void delete() {
        var publisher = new Publisher<ComposedConfiguration<ApplicationId, ApplicationConfiguration>>();
        publisher.observe().doOnNext(event -> log.info("event: {}", event)).subscribe();
        var resolver = new DynamicVirtualHostApplicationIdResolver(publisher);

        var appId = ApplicationId.random();
        publisher.emit(UpdateType.ADD, ApplicationConfiguration.builder()
                .routingDomain("my.domain.test")
                .buildForApplication(appId)
        );
        assertThat(resolver.resolveApplicationId(exchange("https://my.domain.test/foo"))).isNotEmpty();

        publisher.emit(UpdateType.REMOVE, ApplicationConfiguration.builder()
                .routingDomain("my.domain.test")
                .buildForApplication(appId)
        );
        assertThat(resolver.resolveApplicationId(exchange("https://my.domain.test/foo"))).isEmpty();
    }

    private static ServerWebExchange exchange(String uri) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(uri));
    }

}