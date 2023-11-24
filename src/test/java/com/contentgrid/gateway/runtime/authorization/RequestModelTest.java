package com.contentgrid.gateway.runtime.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class RequestModelTest {
    @Test
    void createFromRequest() {

        var request = MockServerHttpRequest.get("http://localhost:123/abc/123/")
                .queryParam("xyz", "abc", "def")
                .header("Content-Type", "application/json", "application/xml")
                .build();

        var model = RequestModel.from(request);

        assertThat(model.getMethod()).isEqualTo("GET");
        assertThat(model.getPath()).isEqualTo(List.of("abc", "123"));
        assertThat(model.getQuery()).isEqualTo(Map.of("xyz", List.of("abc", "def")));
        assertThat(model.getHeaders()).isEqualTo(Map.of("content-type",  List.of("application/json", "application/xml")));
    }

    @Test
    void createFromRequestWithoutPath() {
        var request = MockServerHttpRequest.get("http://localhost:123")
                .build();

        var model = RequestModel.from(request);

        assertThat(model.getPath()).isEqualTo(List.of());
    }

    @ParameterizedTest
    @MethodSource
    void createFromRequestWithWeirdPath(String path, List<String> expected) {
        var request = MockServerHttpRequest.get("http://localhost:123/"+path)
                .build();

        var model = RequestModel.from(request);

        assertThat(model.getPath()).isEqualTo(expected);
    }

    static Stream<Arguments> createFromRequestWithWeirdPath() {
        return Stream.of(
                Arguments.of("///abc", List.of("abc")),
                Arguments.of("xyz/../def", List.of("def")),
                Arguments.of("xyz/./", List.of("xyz")),
                Arguments.of("xyz/./def", List.of("xyz", "def"))
        );
    }
}