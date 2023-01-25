package eu.xenit.alfred.content.gateway.security;

import eu.xenit.alfred.content.gateway.runtime.RuntimeRequestResolver;
import eu.xenit.alfred.content.gateway.security.oidc.ContentGridApplicationOAuth2AuthorizationRequestResolver;
import eu.xenit.alfred.content.gateway.security.oidc.DynamicReactiveClientRegistrationRepository;
import eu.xenit.alfred.content.gateway.security.oidc.ReactiveClientRegistrationIdResolver;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.client.ClientsConfiguredCondition;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties.Registration;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesRegistrationAdapter;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2LoginSpec;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
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


@Configuration
class OAuth2ClientConfiguration {


    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled")
    static class RuntimePlatformOAuth2ClientConfiguration {

        @Bean
        ReactiveClientRegistrationIdResolver clientRegistrationIdResolver(RuntimeRequestResolver runtimeRequestResolver,
                OAuth2ClientProperties oauth2ClientProperties) {

            return exchange -> runtimeRequestResolver
                    .resolveApplicationId(exchange)

                    // temporary implementation, defaulting to the first client-registration
                    .flatMap(appId -> OAuth2ClientPropertiesRegistrationAdapter
                            .getClientRegistrations(oauth2ClientProperties)
                            .values().stream()
                            .map(ClientRegistration::getRegistrationId)
                            .findFirst())
                    .map(Mono::just)
                    .orElse(Mono.empty());
        }

        @Bean
        ReactiveClientRegistrationRepository clientRegistrationRepository(OAuth2ClientProperties properties) {
            var registrations = OAuth2ClientPropertiesRegistrationAdapter.getClientRegistrations(properties).values();
            return new DynamicReactiveClientRegistrationRepository(registrations);
        }

        @Bean
        @ConditionalOnBean(ReactiveClientRegistrationIdResolver.class)
        ContentGridApplicationOAuth2AuthorizationRequestResolver runtimePlatformOAuth2AuthorizationRequestResolver(
                ReactiveClientRegistrationIdResolver clientRegistrationIdResolver,
                ReactiveClientRegistrationRepository clientRegistrationRepository) {
            return new ContentGridApplicationOAuth2AuthorizationRequestResolver(clientRegistrationIdResolver,
                    clientRegistrationRepository);
        }

        @Bean
        @ConditionalOnBean(ContentGridApplicationOAuth2AuthorizationRequestResolver.class)
        Customizer<OAuth2LoginSpec> runtimePlatformOAuth2LoginCustomizer(
                ContentGridApplicationOAuth2AuthorizationRequestResolver authorizationRequestResolver) {
            // responsible to construct AuthorizationRequest from ServerWebExchange
            return spec -> spec
                    .authorizationRequestResolver(authorizationRequestResolver);
        }

        @Bean
        @ConditionalOnBean(ReactiveClientRegistrationIdResolver.class)
        Customizer<List<DelegateEntry>> customizerDynamicOAuth2Entrypoint(
                ReactiveClientRegistrationIdResolver clientRegistrationIdResolver) {
            return entryPoints -> {
                var oauth2loginMatcher = oauth2loginMatcher();
                var entryPoint = new DynamicRedirectServerAuthenticationEntryPoint(exchange -> {
                    // Dynamically look up the registration-id
                    // Note: the actual registration-id is strictly cosmetic at this stage,
                    // because `authorizationRequestResolver` will resolve app-id -> registration-id again
                    return clientRegistrationIdResolver.resolveRegistrationId(exchange)
                            .map("/oauth2/authorization/%s"::formatted)
                            .map(URI::create);
                });

                entryPoints.add(0, new DelegateEntry(oauth2loginMatcher, entryPoint));
            };
        }
    }

    @Bean
    @Conditional(ClientsConfiguredCondition.class)
    Customizer<OAuth2LoginSpec> staticOAuth2Login() {
        return oAuth2LoginSpec -> {
            // no-op is fine, this will load defaults
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

        var oauth2loginMatcher = new AndServerWebExchangeMatcher(
                notXhrMatcher,
                new NegatedServerWebExchangeMatcher(defaultLoginPageMatcher)
        );
        return oauth2loginMatcher;
    }

}
