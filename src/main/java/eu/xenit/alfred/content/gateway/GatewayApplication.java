package eu.xenit.alfred.content.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xenit.alfred.content.gateway.cors.CorsConfigurationResolver;
import eu.xenit.alfred.content.gateway.cors.CorsResolverProperties;
import eu.xenit.alfred.content.gateway.error.ProxyUpstreamUnavailableWebFilter;
import com.contentgrid.opa.client.OpaClient;
import com.contentgrid.opa.client.rest.RestClientConfiguration.LogSpecification;
import com.contentgrid.thunx.pdp.PolicyDecisionComponentImpl;
import com.contentgrid.thunx.pdp.PolicyDecisionPointClient;
import com.contentgrid.thunx.pdp.opa.OpenPolicyAgentPDPClient;
import com.contentgrid.thunx.spring.gateway.filter.AbacGatewayFilterFactory;
import com.contentgrid.thunx.spring.security.ReactivePolicyAuthorizationManager;
import eu.xenit.alfred.content.gateway.routing.ServiceTracker;
import eu.xenit.alfred.content.gateway.servicediscovery.KubernetesServiceDiscovery;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.reactive.EndpointRequest;
import org.springframework.boot.actuate.autoconfigure.security.reactive.EndpointRequest.EndpointServerWebExchangeMatcher;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.actuate.trace.http.HttpTraceRepository;
import org.springframework.boot.actuate.trace.http.InMemoryHttpTraceRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties.Registration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authorization.AuthenticatedReactiveAuthorizationManager;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

@Slf4j
@SpringBootApplication
@EnableConfigurationProperties({OpaProperties.class, CorsResolverProperties.class})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Configuration
    @ConditionalOnProperty("testing.bootstrap.enable")
    static class FakeUsersConfiguration {

        private static final String NOOP_PASSWORD = "{noop}";

        @Bean
        public MapReactiveUserDetailsService userDetailsService(BootstrapProperties bootstrapProperties) {
            List<BootstrapUser> users = bootstrapProperties.getUsers();
            users.forEach(user -> log.info("Bootstrapping user '{}' with authorities {}", user.getUsername(), user.getAuthorities()));
            ObjectMapper mapper = new ObjectMapper();
            return new MapReactiveUserDetailsService(users.stream()
                    .map(user -> user.convert(mapper))
                    .collect(Collectors.toList()));
        }

        @Data
        @ConfigurationProperties(prefix = "testing.bootstrap")
        @Component
        private static class BootstrapProperties {
            List<BootstrapUser> users;
        }

        @Data
        private static class BootstrapUser {
            String username;
            String authorities;

            public UserDetails convert(ObjectMapper mapper) {
                try {
                    TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {
                    };
                    return User
                            .withUsername(username)
                            .password(NOOP_PASSWORD + username)
                            .authorities(new OAuth2UserAuthority(mapper.readValue(authorities, typeRef)))
                            .build();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    @Bean
    HttpTraceRepository traceRepository() {
        return new InMemoryHttpTraceRepository();
    }

    @Bean
    public ServerLogoutSuccessHandler logoutSuccessHandler(
            Optional<ReactiveClientRegistrationRepository> clientRegistrationRepository) {
        if (clientRegistrationRepository.isPresent()) {
            OidcClientInitiatedServerLogoutSuccessHandler oidcLogoutSuccessHandler =
                    new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository.get());
            oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
            return oidcLogoutSuccessHandler;
        } else {
            return new RedirectServerLogoutSuccessHandler();
        }
    }

    @Bean
    @ConditionalOnProperty("opa.service.url")
    public OpaClient opaClient(OpaProperties opaProperties) {
        return OpaClient.builder()
                .httpLogging(LogSpecification::all)
                .url(opaProperties.getService().getUrl())
                .build();
    }

    @Bean
    @ConditionalOnBean(OpaClient.class)
    public PolicyDecisionPointClient pdpClient(OpaProperties opaProperties, OpaClient opaClient, ServiceTracker serviceTracker) {
        return new OpenPolicyAgentPDPClient(opaClient, request -> serviceTracker.opaQueryFor(request));
    }

    @Bean
    @ConditionalOnBean(PolicyDecisionPointClient.class)
    public ReactiveAuthorizationManager<AuthorizationContext> reactiveAuthenticationManager(
            PolicyDecisionPointClient pdpClient) {
        return new ReactivePolicyAuthorizationManager(new PolicyDecisionComponentImpl(pdpClient));
    }

    @Bean
    @ConditionalOnMissingBean(ReactiveAuthorizationManager.class)
    public ReactiveAuthorizationManager<AuthorizationContext> fallbackReactiveAuthenticationManager() {
        log.warn("OpenPolicyAgent not configured, authorization disabled");
        return AuthenticatedReactiveAuthorizationManager.authenticated();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsResolverProperties corsResolverProperties) {
        return new CorsConfigurationResolver(corsResolverProperties);
    }

    @Bean
    public SecurityWebFilterChain springWebFilterChain(
            ServerHttpSecurity http,
            Environment environment,
            ReactiveAuthorizationManager<AuthorizationContext> authorizationManager,
            ServerLogoutSuccessHandler logoutSuccessHandler,
            CorsConfigurationSource corsConfig
    ) {
        EndpointServerWebExchangeMatcher allowedEndpoints = EndpointRequest.to(
                InfoEndpoint.class,
                HealthEndpoint.class,
                MetricsEndpoint.class,
                PrometheusScrapeEndpoint.class
        );
        http.authorizeExchange(exchange -> exchange
                // requests to the actuators /info, /health, /metrics, and /prometheus are allowed unauthenticated
                .matchers(allowedEndpoints).permitAll()

                // requests FROM localhost to actuator endpoints are all permitted
                .matchers(new AndServerWebExchangeMatcher(
                        EndpointRequest.toAnyEndpoint(),
                        mgmtExchange -> {
                            if (mgmtExchange.getRequest().getRemoteAddress().getAddress().isLoopbackAddress()) {
                                return ServerWebExchangeMatcher.MatchResult.match();
                            }
                            return ServerWebExchangeMatcher.MatchResult.notMatch();
                        })).permitAll()

                // other requests must pass through the authorizationManager (opa/keycloak)
                .anyExchange().access(authorizationManager)
        );

        if (OAuth2ResourceServerGuard.shouldConfigure(environment)) {
            http.oauth2ResourceServer(ServerHttpSecurity.OAuth2ResourceServerSpec::jwt);
        }

        if (OAuth2ClientRegistrationsGuard.shouldConfigure(environment)) {
            http.oauth2Login();
            http.oauth2Client();
            http.logout(logout -> logout.logoutSuccessHandler(logoutSuccessHandler));
        } else {
            http.httpBasic();
            http.formLogin();
        }

        http.cors(cors -> cors.configurationSource(corsConfig));

        http.csrf(CsrfSpec::disable);
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.mode(Mode.SAMEORIGIN)));

        return http.build();
    }

    @Bean
    public AbacGatewayFilterFactory abacGatewayFilterFactory() {
        return new AbacGatewayFilterFactory();
    }

    @Bean
    public GlobalFilter proxyUpstreamUnavailableWebFilter()  {
        return new ProxyUpstreamUnavailableWebFilter();
    }

    @Bean
    ApplicationRunner runner(KubernetesServiceDiscovery serviceDiscovery) {
        return args -> serviceDiscovery.discoverApis();
    }

    @Bean
    KubernetesServiceDiscovery serviceDiscovery(@Value("${servicediscovery.namespace:default}") String namespace,
            ServiceTracker serviceTracker) {
        KubernetesClient client = new KubernetesClientBuilder().build();
        return new KubernetesServiceDiscovery(client, namespace, serviceTracker, serviceTracker);
    }

    @Bean
    public ServiceTracker serviceTracker(ApplicationEventPublisher publisher, RouteLocatorBuilder builder) {
        return new ServiceTracker(publisher, builder);
    }

    private static class OAuth2ClientRegistrationsGuard {

        private static final Bindable<Map<String, Registration>> STRING_REGISTRATION_MAP = Bindable
                .mapOf(String.class, OAuth2ClientProperties.Registration.class);

        /**
         * Checks if any {@code spring.security.oauth2.client.registration} properties are defined.
         *
         * @return {@code true} if any {@code spring.security.oauth2.client.registration} properties are defined.
         */
        static boolean shouldConfigure(Environment environment) {
            Map<String, Registration> registrations = getRegistrations(environment);
            return !registrations.isEmpty();
        }

        private static Map<String, OAuth2ClientProperties.Registration> getRegistrations(Environment environment) {
            return Binder.get(environment).bind("spring.security.oauth2.client.registration", STRING_REGISTRATION_MAP)
                    .orElse(Collections.emptyMap());
        }

    }

    private static class OAuth2ResourceServerGuard {
        public static boolean shouldConfigure(Environment environment) {
            return environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri") != null;
        }
    }

}
