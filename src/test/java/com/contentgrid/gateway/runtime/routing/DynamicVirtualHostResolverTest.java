package com.contentgrid.gateway.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.routing.DynamicVirtualHostResolver.ApplicationDomainNameEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import reactor.test.publisher.TestPublisher;

@Slf4j
class DynamicVirtualHostResolverTest {

    @Test
    void resolveDomains() {
        TestPublisher<ApplicationDomainNameEvent> publisher = TestPublisher.create();
        var flux = publisher.flux().doOnNext(event -> log.info("event: {}", event));
        var resolver = new DynamicVirtualHostResolver(flux, event -> {});
        var appId = ApplicationId.random();

        // verify nothing gets resolved
        assertThat(resolver.resolve("https://primary.contentgrid.test/foo")).isEmpty();
        assertThat(resolver.resolve("https://secondary.domain.test/foo")).isEmpty();
        assertThat(resolver.resolve("https://alternative.domain.test/foo")).isEmpty();

        // add primary and secondary domain
        publisher.next(ApplicationDomainNameEvent.put(appId, "primary.contentgrid.test", "secondary.domain.test"));
        assertThat(resolver.resolve("https://primary.contentgrid.test/foo")).hasValue(appId);
        assertThat(resolver.resolve("https://secondary.domain.test/foo")).hasValue(appId);
        assertThat(resolver.resolve("https://alternative.domain.test/foo")).isEmpty();

        // remove primary domain, add alternative domain
        publisher.next(ApplicationDomainNameEvent.put(appId, "alternative.domain.test", "secondary.domain.test"));
        assertThat(resolver.resolve("https://primary.contentgrid.test/foo")).isEmpty();
        assertThat(resolver.resolve("https://secondary.domain.test/foo")).hasValue(appId);
        assertThat(resolver.resolve("https://alternative.domain.test/foo")).hasValue(appId);
    }

    @Test
    void hijackDomain() {
        TestPublisher<ApplicationDomainNameEvent> publisher = TestPublisher.create();
        var flux = publisher.flux().doOnNext(event -> log.info("event: {}", event));
        var resolver = new DynamicVirtualHostResolver(flux, event -> {});

        var app1 = ApplicationId.random();
        publisher.next(ApplicationDomainNameEvent.put(app1, "my.domain.test"));
        assertThat(resolver.resolve("https://my.domain.test/foo")).hasValue(app1);

        // app2 tries to hijack the domain
        var app2 = ApplicationId.random();
        publisher.next(ApplicationDomainNameEvent.put(app2, "my.domain.test"));

        // err on the side of caution - don't route any domains in case of a conflict
        assertThat(resolver.resolve("https://my.domain.test/foo")).isEmpty();

    }

    @Test
    void delete() {
        TestPublisher<ApplicationDomainNameEvent> publisher = TestPublisher.create();
        var flux = publisher.flux().doOnNext(event -> log.info("event: {}", event));
        var resolver = new DynamicVirtualHostResolver(flux, event -> {});

        var appId = ApplicationId.random();
        publisher.next(ApplicationDomainNameEvent.put(appId, "my.domain.test"));
        assertThat(resolver.resolve("https://my.domain.test/foo")).isNotEmpty();

        publisher.next(ApplicationDomainNameEvent.delete(appId));
        assertThat(resolver.resolve("https://my.domain.test/foo")).isEmpty();
    }

    @Test
    void clear() {
        TestPublisher<ApplicationDomainNameEvent> publisher = TestPublisher.create();
        var flux = publisher.flux().doOnNext(event -> log.info("event: {}", event));
        var resolver = new DynamicVirtualHostResolver(flux, event -> {});

        publisher.next(ApplicationDomainNameEvent.put(ApplicationId.random(), "my.domain.test"));
        assertThat(resolver.resolve("https://my.domain.test/foo")).isNotEmpty();

        publisher.next(ApplicationDomainNameEvent.clear());
        assertThat(resolver.resolve("https://my.domain.test/foo")).isEmpty();
    }

}