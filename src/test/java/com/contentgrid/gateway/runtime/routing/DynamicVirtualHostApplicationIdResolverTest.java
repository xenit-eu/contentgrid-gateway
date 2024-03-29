package com.contentgrid.gateway.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.routing.DynamicVirtualHostApplicationIdResolver.ApplicationDomainNameEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.publisher.TestPublisher;

@Slf4j
class DynamicVirtualHostApplicationIdResolverTest {

    @Test
    void resolveDomains() {
        TestPublisher<ApplicationDomainNameEvent> publisher = TestPublisher.create();
        var flux = publisher.flux().doOnNext(event -> log.info("event: {}", event));
        var resolver = new DynamicVirtualHostApplicationIdResolver(flux, event -> {});
        var appId = ApplicationId.random();

        // verify nothing gets resolved
        assertThat(resolver.resolveApplicationId(exchange("https://primary.contentgrid.test/foo"))).isEmpty();
        assertThat(resolver.resolveApplicationId(exchange("https://secondary.domain.test/foo"))).isEmpty();
        assertThat(resolver.resolveApplicationId(exchange("https://alternative.domain.test/foo"))).isEmpty();

        // add primary and secondary domain
        publisher.next(ApplicationDomainNameEvent.put(appId, "primary.contentgrid.test", "secondary.domain.test"));
        assertThat(resolver.resolveApplicationId(exchange("https://primary.contentgrid.test/foo"))).hasValue(appId);
        assertThat(resolver.resolveApplicationId(exchange("https://secondary.domain.test/foo"))).hasValue(appId);
        assertThat(resolver.resolveApplicationId(exchange("https://alternative.domain.test/foo"))).isEmpty();

        // remove primary domain, add alternative domain
        publisher.next(ApplicationDomainNameEvent.put(appId, "alternative.domain.test", "secondary.domain.test"));
        assertThat(resolver.resolveApplicationId(exchange("https://primary.contentgrid.test/foo"))).isEmpty();
        assertThat(resolver.resolveApplicationId(exchange("https://secondary.domain.test/foo"))).hasValue(appId);
        assertThat(resolver.resolveApplicationId(exchange("https://alternative.domain.test/foo"))).hasValue(appId);
    }

    @Test
    void hijackDomain() {
        TestPublisher<ApplicationDomainNameEvent> publisher = TestPublisher.create();
        var flux = publisher.flux().doOnNext(event -> log.info("event: {}", event));
        var resolver = new DynamicVirtualHostApplicationIdResolver(flux, event -> {});

        var app1 = ApplicationId.random();
        publisher.next(ApplicationDomainNameEvent.put(app1, "my.domain.test"));
        assertThat(resolver.resolveApplicationId(exchange("https://my.domain.test/foo"))).hasValue(app1);

        // app2 tries to hijack the domain
        var app2 = ApplicationId.random();
        publisher.next(ApplicationDomainNameEvent.put(app2, "my.domain.test"));

        // err on the side of caution - don't route any domains in case of a conflict
        assertThat(resolver.resolveApplicationId(exchange("https://my.domain.test/foo"))).isEmpty();

    }

    @Test
    void delete() {
        TestPublisher<ApplicationDomainNameEvent> publisher = TestPublisher.create();
        var flux = publisher.flux().doOnNext(event -> log.info("event: {}", event));
        var resolver = new DynamicVirtualHostApplicationIdResolver(flux, event -> {});

        var appId = ApplicationId.random();
        publisher.next(ApplicationDomainNameEvent.put(appId, "my.domain.test"));
        assertThat(resolver.resolveApplicationId(exchange("https://my.domain.test/foo"))).isNotEmpty();

        publisher.next(ApplicationDomainNameEvent.delete(appId));
        assertThat(resolver.resolveApplicationId(exchange("https://my.domain.test/foo"))).isEmpty();
    }

    @Test
    void clear() {
        TestPublisher<ApplicationDomainNameEvent> publisher = TestPublisher.create();
        var flux = publisher.flux().doOnNext(event -> log.info("event: {}", event));
        var resolver = new DynamicVirtualHostApplicationIdResolver(flux, event -> {});

        publisher.next(ApplicationDomainNameEvent.put(ApplicationId.random(), "my.domain.test"));
        assertThat(resolver.resolveApplicationId(exchange("https://my.domain.test/foo"))).isNotEmpty();

        publisher.next(ApplicationDomainNameEvent.clear());
        assertThat(resolver.resolveApplicationId(exchange("https://my.domain.test/foo"))).isEmpty();
    }

    private static ServerWebExchange exchange(String uri) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(uri));
    }

}