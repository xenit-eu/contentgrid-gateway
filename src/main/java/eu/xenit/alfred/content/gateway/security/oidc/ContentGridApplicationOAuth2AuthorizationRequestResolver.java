package eu.xenit.alfred.content.gateway.security.oidc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
public class ContentGridApplicationOAuth2AuthorizationRequestResolver
        extends DefaultServerOAuth2AuthorizationRequestResolver
        implements ServerOAuth2AuthorizationRequestResolver {

    private final ReactiveClientRegistrationIdResolver clientRegistrationIdResolver;
    private final ServerWebExchangeMatcher authorizationRequestMatcher;

    public ContentGridApplicationOAuth2AuthorizationRequestResolver(
            ReactiveClientRegistrationIdResolver clientRegistrationIdResolver,
            ReactiveClientRegistrationRepository clientRegistrationRepository) {
        this(clientRegistrationIdResolver, clientRegistrationRepository,
                new PathPatternParserServerWebExchangeMatcher(DEFAULT_AUTHORIZATION_REQUEST_PATTERN));

    }

    public ContentGridApplicationOAuth2AuthorizationRequestResolver(
            ReactiveClientRegistrationIdResolver clientRegistrationIdResolver,
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            ServerWebExchangeMatcher matcher) {
        super(clientRegistrationRepository, matcher);

        this.clientRegistrationIdResolver = clientRegistrationIdResolver;
        this.authorizationRequestMatcher = matcher;
    }

    // resolve the request that is going to redirect us to keycloak

    @Override
    public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange) {
        // we only know the exchange and can lookup the idp info
        // idp info lookup: app-id -> client-registration-id
        return this.authorizationRequestMatcher
                .matches(exchange)
                .filter(MatchResult::isMatch)
                .flatMap((match) -> this.clientRegistrationIdResolver.resolveRegistrationId(exchange))
                .flatMap((clientRegistrationId) -> resolve(exchange, clientRegistrationId));
    }
}
