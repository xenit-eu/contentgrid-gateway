package com.contentgrid.gateway.runtime.security.oidc;

import com.contentgrid.configuration.api.ComposedConfiguration;
import com.contentgrid.configuration.api.observable.Observable;
import com.contentgrid.configuration.applications.ApplicationConfiguration;
import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.routing.ApplicationIdRequestResolver;
import com.contentgrid.gateway.security.oauth2.client.registration.DynamicReactiveClientRegistrationRepository;
import com.contentgrid.gateway.security.oauth2.client.registration.DynamicReactiveClientRegistrationRepository.ClientRegistrationEvent;
import com.contentgrid.gateway.security.oidc.ContentGridApplicationOAuth2AuthorizationRequestResolver;
import com.contentgrid.gateway.security.oidc.DynamicRedirectServerAuthenticationEntryPoint;
import com.contentgrid.gateway.security.oidc.OAuth2ClientApplicationConfigurationMapper;
import com.contentgrid.gateway.security.oidc.ReactiveClientRegistrationIdResolver;
import com.contentgrid.gateway.security.oidc.ReactiveClientRegistrationResolver;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2LoginSpec;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint.DelegateEntry;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.MediaTypeServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled")
@Slf4j
public class RuntimePlatformOidcClientConfiguration {

    @Bean
    ReactiveClientRegistrationIdResolver clientRegistrationIdResolver() {

        // uses the app-id as registration-id !!
        return applicationId -> Mono
                .just(applicationId.toString())
                .map("client-%s"::formatted);
    }

    @Bean
    ReactiveClientRegistrationResolver oAuth2ClientApplicationConfigurationMapper(
            ReactiveClientRegistrationIdResolver registrationIdResolver) {
        return new OAuth2ClientApplicationConfigurationMapper(registrationIdResolver);
    }

    @Bean
    ReactiveClientRegistrationRepository clientRegistrationRepository(
            ReactiveClientRegistrationIdResolver registrationIdResolver,
            ReactiveClientRegistrationResolver clientRegistrationResolver,
            Observable<ComposedConfiguration<ApplicationId, ApplicationConfiguration>> applicationConfigurationRepository) {

        return new DynamicReactiveClientRegistrationRepository(applicationConfigurationRepository
                .observe()
                .flatMap(update -> registrationIdResolver.resolveRegistrationId(update.getValue().getCompositionKey())
                        .map(registrationId -> switch (update.getType()) {
                            case ADD, UPDATE -> ClientRegistrationEvent.put(registrationId,
                                    clientRegistrationResolver.buildClientRegistration(update.getValue()));
                            case REMOVE -> ClientRegistrationEvent.delete(registrationId);
                        }))
                .doOnNext(
                        event -> log.debug("ReactiveClientRegistrationRepository -> {}", event))
        );
    }

    @Bean
    @ConditionalOnBean(ReactiveClientRegistrationIdResolver.class)
    ContentGridApplicationOAuth2AuthorizationRequestResolver runtimePlatformOAuth2AuthorizationRequestResolver(
            ApplicationIdRequestResolver applicationIdResolver,
            ReactiveClientRegistrationIdResolver clientRegistrationIdResolver,
            ReactiveClientRegistrationRepository clientRegistrationRepository) {
        return new ContentGridApplicationOAuth2AuthorizationRequestResolver(
                applicationIdResolver, clientRegistrationIdResolver,
                clientRegistrationRepository);
    }

    @Bean
    @ConditionalOnBean(ContentGridApplicationOAuth2AuthorizationRequestResolver.class)
    Customizer<OAuth2LoginSpec> runtimePlatformOAuth2LoginCustomizer(
            ContentGridApplicationOAuth2AuthorizationRequestResolver authorizationRequestResolver) {
        // responsible to construct AuthorizationRequest from ServerWebExchange
        return spec -> spec.authorizationRequestResolver(authorizationRequestResolver);
    }

    @Bean
    @ConditionalOnBean(ReactiveClientRegistrationIdResolver.class)
    Customizer<List<DelegateEntry>> customizerDynamicOAuth2Entrypoint(
            ReactiveClientRegistrationIdResolver clientRegistrationIdResolver,
            ApplicationIdRequestResolver applicationIdResolver) {
        return entryPoints -> {
            var matcher = new AndServerWebExchangeMatcher(
                    applicationIdResolver.matcher(),
                    oauth2loginMatcher()
            );

            var entryPoint = new DynamicRedirectServerAuthenticationEntryPoint(exchange -> {
                // Dynamically look up the registration-id, so the user gets an
                // HTTP 302 to /oauth2/authorization/<registrationId>
                // Note: the actual registration-id is cosmetic at this stage, because when handling
                // the request to the above URI, the `authorizationRequestResolver` will resolve
                // application-id to registration-id again
                return Mono.justOrEmpty(applicationIdResolver.resolveApplicationId(exchange))
                        .switchIfEmpty(Mono.fromRunnable(() -> log.warn(
                                "Entered oauth2login entrypoint, but could not resolve app-id for request {} {}",
                                exchange.getRequest().getMethod(), exchange.getRequest().getURI())))
                        .flatMap(clientRegistrationIdResolver::resolveRegistrationId)
                        .map("/oauth2/authorization/%s"::formatted)
                        .map(URI::create);
            });

            entryPoints.add(new DelegateEntry(matcher, entryPoint));
        };
    }

    private static ServerWebExchangeMatcher oauth2loginMatcher() {
        var htmlMatcher = new MediaTypeServerWebExchangeMatcher(
                MediaType.APPLICATION_XHTML_XML,
                new MediaType("image", "*"),
                MediaType.TEXT_HTML,
                MediaType.TEXT_PLAIN);
        htmlMatcher.setIgnoredMediaTypes(Collections.singleton(MediaType.ALL));

        ServerWebExchangeMatcher xhrMatcher = exchange -> {
            if (exchange.getRequest().getHeaders().getOrEmpty("X-Requested-With")
                    .contains("XMLHttpRequest")) {
                return MatchResult.match();
            }

            return MatchResult.notMatch();
        };

        var notXhrMatcher = new NegatedServerWebExchangeMatcher(xhrMatcher);

        var defaultEntryPointMatcher = new AndServerWebExchangeMatcher(notXhrMatcher, htmlMatcher);

        var loginPageMatcher = new PathPatternParserServerWebExchangeMatcher("/login");
        var faviconMatcher = new PathPatternParserServerWebExchangeMatcher("/favicon.ico");
        var defaultLoginPageMatcher = new AndServerWebExchangeMatcher(
                new OrServerWebExchangeMatcher(loginPageMatcher, faviconMatcher),
                defaultEntryPointMatcher);

        return new AndServerWebExchangeMatcher(
                notXhrMatcher,
                htmlMatcher,
                new NegatedServerWebExchangeMatcher(defaultLoginPageMatcher)
        );
    }
}
