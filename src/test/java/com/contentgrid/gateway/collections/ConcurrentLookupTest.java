package com.contentgrid.gateway.collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConcurrentLookupTest {

    @Test
    void simpleOperations() {
        var map = new ConcurrentLookup<String, String>(String::toUpperCase);
        map.add("foo");
        map.add("bar");
        map.add("foo");

        assertThat(map.get("foo")).isNull();
        assertThat(map.get("FOO")).isEqualTo("foo");
        assertThat(map.size()).isEqualTo(2);

        map.remove("bar");
        assertThat(map.size()).isEqualTo(2);
        map.remove("BAR");
        assertThat(map.size()).isEqualTo(1);

        map.clear();
        assertThat(map.size()).isEqualTo(0);
    }


    @Test
    void stream() {

        var map = new ConcurrentLookup<String, String>(String::toUpperCase);
        map.createLookup(String::length);

        map.add("foo");
        var stream = map.stream();
        map.add("bar");

        assertThat(stream).hasSize(2);

        var stream2 = map.stream();
        map.clear();
        assertThat(stream2).isEmpty();
    }

    @Test
    void createLookup() {

        var map = new ConcurrentLookup<String, String>(String::toUpperCase);
        var lengthLookup = map.createLookup(String::length);

        map.add("foo");
        map.add("bar");
        map.add("foobar");

        assertThat(lengthLookup.apply(3)).containsExactlyInAnyOrder("foo", "bar");
        assertThat(lengthLookup.apply(4)).isEmpty();
        assertThat(lengthLookup.apply(5)).isEmpty();
        assertThat(lengthLookup.apply(6)).contains("foobar");

        map.remove("BAR");
        assertThat(lengthLookup.apply(3)).containsExactly("foo");

        map.clear();
        assertThat(lengthLookup.apply(3)).isEmpty();

    }

    @Test
    void createMultiLookup() {

        var map = new ConcurrentLookup<String, String>(String::toUpperCase);
        var letterLookup = map.createMultiLookup(str -> Arrays.stream(str.split("")));

        map.add("foo");
        map.add("bar");
        map.add("foobar");
        map.add("baz");

        assertThat(letterLookup.apply("f")).containsExactlyInAnyOrder("foo", "foobar");
        assertThat(letterLookup.apply("b")).containsExactlyInAnyOrder("bar", "foobar", "baz");
        assertThat(letterLookup.apply("o")).containsExactlyInAnyOrder("foo", "foobar");
        assertThat(letterLookup.apply("z")).containsExactly("baz");

        map.remove("FOOBAR");

        assertThat(letterLookup.apply("f")).containsExactly("foo");
        assertThat(letterLookup.apply("b")).containsExactlyInAnyOrder("bar", "baz");
        assertThat(letterLookup.apply("o")).containsExactly("foo");

        map.clear();
        assertThat(letterLookup.apply("f")).isEmpty();
        assertThat(letterLookup.apply("b")).isEmpty();
    }
}