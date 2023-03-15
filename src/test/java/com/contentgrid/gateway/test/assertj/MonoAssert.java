package com.contentgrid.gateway.test.assertj;

import org.assertj.core.api.AbstractAssert;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class MonoAssert<T> extends AbstractAssert<MonoAssert<T>, Mono<T>> {

    public static <T> MonoAssert<T> assertThat(Mono<T> mono) {
        return new MonoAssert<T>(mono);
    }

    MonoAssert(Mono<T> actual) {
        super(actual, MonoAssert.class);
    }

    public MonoAssert<T> isEmptyMono() {
        this.isNotNull();
        StepVerifier.create(actual).verifyComplete();
        return this;
    }

    public MonoAssert<T> hasValue(T value) {
        this.isNotNull();
        StepVerifier.create(actual).expectNext(value).verifyComplete();
        return this;
    }
}
