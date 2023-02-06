package eu.xenit.alfred.content.gateway.collections;

import static org.assertj.core.api.Assertions.assertThat;

import eu.xenit.alfred.content.gateway.collections.ObservableMap.MapUpdate;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@Slf4j
class ObservableMapTest {

    @Test
    void testBasicMapFunctionality() {
        var map = new ObservableMap<String, String>();
        map.put("key", "value");

        assertThat(map).containsEntry("key", "value");

    }


    @Test
    @SuppressWarnings("OverwrittenKey")
    void observeFromStart() {
        var map = new ObservableMap<String, String>();

        map.observe().subscribe(
                update -> log.info("observer 1 - next: {}", update),
                error -> log.info("observer 1 - error", error),
                () -> log.info("observer 1 - complete"));

        var verifier_start = StepVerifier.create(map.observe())
                .expectSubscription()
                .expectNext(MapUpdate.put("foo", "bar"))
                .expectNext(MapUpdate.put("bar", "baz"))
                .expectNext(MapUpdate.put("foo", "baz"))
                .expectNext(MapUpdate.remove("bar", "baz"))
                .expectNext(MapUpdate.put("bar", "foo"))
                .expectNext(MapUpdate.clear())
                .expectNext(MapUpdate.put("baz", "foo"))
                .expectComplete()
                .log()
                .verifyLater();


        map.put("foo", "bar");
        map.put("bar", "baz");
        map.put("foo", "baz");
        map.remove("bar");

        map.remove("bar"); // does not exists

        map.put("bar", "foo");

        map.observe().subscribe(update -> {
            log.info("observer 2 {}", update);
        });

        var verifier_mid = StepVerifier.create(map.observe())
                .expectSubscription()
                // state right before the subscription
                .expectNext(MapUpdate.put("foo", "baz"))
                .expectNext(MapUpdate.put("bar", "foo"))
                // state changes after subscription
                .expectNext(MapUpdate.clear())
                .expectNext(MapUpdate.put("baz", "foo"))
                .expectComplete()
                .log()
                .verifyLater();

        map.clear();
        map.put("baz", "foo");

        map.observe().subscribe(update -> {
            log.info("observer 3 {}", update);
        });

        var verifier_end = StepVerifier.create(map.observe())
                .expectSubscription()
                // state right before the subscription
                .expectNext(MapUpdate.put("baz", "foo"))
                .expectComplete()
                .log()
                .verifyLater();

        map.close();

        verifier_start.verify(Duration.ofSeconds(1));
        verifier_mid.verify(Duration.ofSeconds(1));
        verifier_end.verify(Duration.ofSeconds(1));

    }
}