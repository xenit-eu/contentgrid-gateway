package com.contentgrid.gateway.runtime.web;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_APP_ID_ATTR;
import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_SERVICE_INSTANCE_ATTR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.test.runtime.ServiceInstanceStubs;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;
import org.springframework.test.json.JsonContent;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

class ContentGridAppNotDeployedWebFilterTest {

    private static final ServerWebExchangeMatcher NOTHING_EXCLUDED = exchange -> MatchResult.notMatch();
    private static final ServerWebExchangeMatcher EVERYTHING_EXCLUDED = exchange -> MatchResult.match();

    private static final List<HttpMessageWriter<?>> MESSAGE_WRITERS = ServerCodecConfigurer.create().getWriters();

    private static final Authentication USER = new TestingAuthenticationToken("user", null, "ROLE_USER");
    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("key", "anonymousUser",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private WebFilterChain chain;

    @BeforeEach
    void setup() {
        this.chain = Mockito.mock(WebFilterChain.class);
        Mockito.when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    void notDeployedApplication_authenticated_http503() {
        var filter = new ContentGridAppNotDeployedWebFilter(NOTHING_EXCLUDED, MESSAGE_WRITERS);
        var exchange = notDeployedExchange();

        StepVerifier.create(filter.filter(exchange, chain).contextWrite(
                ReactiveSecurityContextHolder.withAuthentication(USER))).verifyComplete();

        Mockito.verifyNoInteractions(chain);
        var response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(new JsonContent(response.getBodyAsString().block())).isStrictlyEqualTo("""
                {
                    "type": "about:blank",
                    "title": "Service Unavailable",
                    "status": 503,
                    "detail": "No deployment of this application is currently available"
                }
                """);
    }

    private static Stream<Arguments> requestsThatContinue() {
        return Stream.of(
                Arguments.argumentSet("no authentication", NOTHING_EXCLUDED, notDeployedExchange(), Context.empty()),
                Arguments.argumentSet("anonymous authentication", NOTHING_EXCLUDED, notDeployedExchange(),
                        ReactiveSecurityContextHolder.withAuthentication(ANONYMOUS)),
                Arguments.argumentSet("excluded request", EVERYTHING_EXCLUDED, notDeployedExchange(),
                        ReactiveSecurityContextHolder.withAuthentication(USER)),
                Arguments.argumentSet("deployed application", NOTHING_EXCLUDED, deployedExchange(),
                        ReactiveSecurityContextHolder.withAuthentication(USER))
        );
    }

    @ParameterizedTest
    @MethodSource("requestsThatContinue")
    void continues(ServerWebExchangeMatcher excludedRequests, MockServerWebExchange exchange, Context context) {
        var filter = new ContentGridAppNotDeployedWebFilter(excludedRequests, MESSAGE_WRITERS);

        StepVerifier.create(filter.filter(exchange, chain).contextWrite(context)).verifyComplete();

        Mockito.verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private static MockServerWebExchange notDeployedExchange() {
        var exchange = createExchange();
        exchange.getAttributes().put(CONTENTGRID_APP_ID_ATTR, ApplicationId.random());
        return exchange;
    }

    private static MockServerWebExchange deployedExchange() {
        var appId = ApplicationId.random();
        var exchange = createExchange();
        exchange.getAttributes().put(CONTENTGRID_APP_ID_ATTR, appId);
        exchange.getAttributes().put(CONTENTGRID_SERVICE_INSTANCE_ATTR,
                ServiceInstanceStubs.serviceInstance(DeploymentId.random(), appId));
        return exchange;
    }

    private static MockServerWebExchange createExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("https://my-app.userapps.contentgrid.com/test"));
    }
}
