package eu.xenit.alfred.content.gateway;

import static eu.xenit.alfred.content.gateway.filter.web.ContentGridAppRequestWebFilter.CONTENTGRID_WEB_FILTER_CHAIN_FILTER_ORDER;

import com.contentgrid.opa.client.OpaClient;
import com.contentgrid.opa.client.rest.RestClientConfiguration.LogSpecification;
import com.contentgrid.thunx.pdp.PolicyDecisionComponentImpl;
import com.contentgrid.thunx.pdp.PolicyDecisionPointClient;
import com.contentgrid.thunx.pdp.opa.OpaQueryProvider;
import com.contentgrid.thunx.pdp.opa.OpenPolicyAgentPDPClient;
import com.contentgrid.thunx.spring.gateway.filter.AbacGatewayFilterFactory;
import com.contentgrid.thunx.spring.security.ReactivePolicyAuthorizationManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xenit.alfred.content.gateway.cors.CorsConfigurationResolver;
import eu.xenit.alfred.content.gateway.cors.CorsResolverProperties;
import eu.xenit.alfred.content.gateway.error.ProxyUpstreamUnavailableWebFilter;
import eu.xenit.alfred.content.gateway.filter.web.ContentGridAppRequestWebFilter;
import eu.xenit.alfred.content.gateway.filter.web.ContentGridResponseHeadersWebFilter;
import eu.xenit.alfred.content.gateway.routing.ServiceTracker;
import eu.xenit.alfred.content.gateway.runtime.DefaultRuntimeRequestResolver;
import eu.xenit.alfred.content.gateway.runtime.RuntimeRequestResolver;
import eu.xenit.alfred.content.gateway.runtime.config.kubernetes.KubernetesApplicationConfigurationRepository;
import eu.xenit.alfred.content.gateway.security.oidc.ReactiveClientRegistrationIdResolver;
import eu.xenit.alfred.content.gateway.servicediscovery.ContentGridApplicationMetadata;
import eu.xenit.alfred.content.gateway.servicediscovery.ContentGridDeploymentMetadata;
import eu.xenit.alfred.content.gateway.servicediscovery.KubernetesServiceDiscovery;
import eu.xenit.alfred.content.gateway.servicediscovery.ServiceDiscovery;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.reactive.EndpointRequest;
import org.springframework.boot.actuate.autoconfigure.security.reactive.EndpointRequest.EndpointServerWebExchangeMatcher;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
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
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.Fabric8ServiceInstanceMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.authorization.AuthenticatedReactiveAuthorizationManager;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2LoginSpec;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint.DelegateEntry;
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
@EnableConfigurationProperties({OpaProperties.class, CorsResolverProperties.class, ServiceDiscoveryProperties.class})
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
            users.forEach(user -> log.info("Bootstrapping user '{}' with authorities {}", user.getUsername(),
                    user.getAuthorities()));
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
    ContentGridDeploymentMetadata deploymentMetadata() {
        return new ContentGridDeploymentMetadata() {
            public Optional<String> getApplicationId(@NonNull ServiceInstance service) {
                return Optional.ofNullable(service.getMetadata().get("app.contentgrid.com/application-id"));
            }

            @Override
            public Optional<String> getDeploymentId(ServiceInstance service) {
                return Optional.ofNullable(service.getMetadata().get("app.contentgrid.com/deployment-id"));
            }

            @Override
            public Optional<String> getPolicyPackage(ServiceInstance service) {
                return Optional.ofNullable(service.getMetadata().get("authz.contentgrid.com/policy-package"));
            }
        };
    }

    @Bean
    ContentGridApplicationMetadata applicationMetadata(ContentGridDeploymentMetadata deploymentMetadata) {
        return new ContentGridApplicationMetadata() {

            @Override
            public Optional<String> getApplicationId(ServiceInstance service) {
                return deploymentMetadata.getApplicationId(service);
            }

            @Override
            @Deprecated
            public Set<String> getDomainNames(@NonNull ServiceInstance service) {
                return this.getApplicationId(service)
                        .stream()
                        .map("%s.userapps.contentgrid.com"::formatted)
                        .collect(Collectors.toSet());
            }
        };
    }

    @Bean
    @Order(CONTENTGRID_WEB_FILTER_CHAIN_FILTER_ORDER)
    @ConditionalOnBean(ServiceTracker.class)
    ContentGridAppRequestWebFilter contentGridAppRequestWebFilter(ServiceTracker tracker,
            ContentGridDeploymentMetadata deploymentMetadata,
            ContentGridApplicationMetadata applicationMetadata) {
        return new ContentGridAppRequestWebFilter(tracker, deploymentMetadata, applicationMetadata);
    }

    @Bean
    @Order(CONTENTGRID_WEB_FILTER_CHAIN_FILTER_ORDER + 10)
    ContentGridResponseHeadersWebFilter contentGridResponseHeadersWebFilter() {
        return new ContentGridResponseHeadersWebFilter();
    }

    @Configuration
    @ConditionalOnProperty("servicediscovery.enabled")
    static class ServiceDiscoveryConfiguration {

        @Bean
        ApplicationRunner runner(ServiceDiscovery serviceDiscovery) {
            return args -> serviceDiscovery.discoverApis();
        }

        @Bean
        KubernetesClient kubernetesClient() {
            return new KubernetesClientBuilder().build();
        }

        @Bean
        ServiceDiscovery serviceDiscovery(ServiceDiscoveryProperties properties, KubernetesClient kubernetesClient,
                ServiceTracker serviceTracker, Fabric8ServiceInstanceMapper instanceMapper) {
            return new KubernetesServiceDiscovery(kubernetesClient, properties.getNamespace(), serviceTracker,
                    serviceTracker, instanceMapper);
        }

        @Bean
        public ServiceTracker serviceTracker(ApplicationEventPublisher publisher,
                ContentGridApplicationMetadata applicationMetadata) {
            return new ServiceTracker(publisher, applicationMetadata);
        }

        @Bean
        KubernetesApplicationConfigurationRepository kubernetesApplicationConfigurationRepository(
                KubernetesClient kubernetesClient, ServiceDiscoveryProperties properties) {
            return new KubernetesApplicationConfigurationRepository(kubernetesClient, properties.getNamespace());
        }

        @Bean
        ApplicationRunner k8sWatchSecrets(KubernetesApplicationConfigurationRepository k8sAppConfig) {
            return args -> k8sAppConfig.watchSecrets();
        }


        @Bean
        OpaQueryProvider opaQueryProvider(ServiceTracker serviceTracker,
                ContentGridDeploymentMetadata deploymentMetadata,
                ContentGridApplicationMetadata applicationMetadata) {

            // TARGET ARCH: get application-id from request attributes, not from the service-tracker
            return request -> serviceTracker
                    .services()
                    .filter(service -> {
                        var domainNames = applicationMetadata.getDomainNames(service);
                        return domainNames.contains(request.getURI().getHost());
                    })
                    .flatMap(service -> deploymentMetadata.getPolicyPackage(service)
                            .stream()
                            .map("data.%s.allow == true"::formatted))
                    .findFirst()
                    .orElseGet(() -> {
                        // TODO this should fail !?
                        log.warn(
                                "Request for unknown host ({}), perhaps the gateway itself, using tautological opa query",
                                request.getURI().getHost());
                        return "1 == 1";
                    });
        }
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
    @ConditionalOnProperty(value = "servicediscovery.enabled", havingValue = "false", matchIfMissing = true)
    OpaQueryProvider opaQueryProvider(OpaProperties opaProperties) {
        return request -> opaProperties.getQuery();
    }

    @Bean
    @ConditionalOnBean(OpaClient.class)
    public PolicyDecisionPointClient pdpClient(OpaClient opaClient, OpaQueryProvider opaQueryProvider) {
        return new OpenPolicyAgentPDPClient(opaClient, opaQueryProvider);
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
    @ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled")
    RuntimeRequestResolver runtimeRequestResolver() {
        return new DefaultRuntimeRequestResolver();
    }


    @Bean
    public SecurityWebFilterChain springWebFilterChain(
            ServerHttpSecurity http,
            Environment environment,
            ReactiveAuthorizationManager<AuthorizationContext> authorizationManager,
            ServerLogoutSuccessHandler logoutSuccessHandler,
            CorsConfigurationSource corsConfig,
            List<Customizer<OAuth2LoginSpec>> oauth2loginCustomizer,
            List<Customizer<List<DelegateEntry>>> authenticationEntryPointCustomizer
    ) {
        http.authorizeExchange(exchange -> exchange
                // requests to the actuators /info, /health, /metrics, and /prometheus are allowed unauthenticated
                .matchers(EndpointRequest.to(
                        InfoEndpoint.class,
                        HealthEndpoint.class,
                        MetricsEndpoint.class,
                        PrometheusScrapeEndpoint.class
                )).permitAll()

                // requests FROM localhost to actuator endpoints are all permitted
                .matchers(new AndServerWebExchangeMatcher(
                        EndpointRequest.toAnyEndpoint(),
                        mgmtExchange -> {
                            if (mgmtExchange.getRequest().getRemoteAddress().getAddress().isLoopbackAddress()) {
                                return ServerWebExchangeMatcher.MatchResult.match();
                            }
                            return ServerWebExchangeMatcher.MatchResult.notMatch();
                        })
                ).permitAll()

                // other requests must pass through the authorizationManager (opa/keycloak)
                .anyExchange().access(authorizationManager)
        );

        // Bearer token auth
        if (OAuth2ResourceServerGuard.shouldConfigure(environment)) {
            http.oauth2ResourceServer(ServerHttpSecurity.OAuth2ResourceServerSpec::jwt);
        }

        // OAuth2 login
        oauth2loginCustomizer.forEach(http::oauth2Login);

        // if there are no authentication customizers, fallback to http-basic
        if (oauth2loginCustomizer.isEmpty()) {
            http.httpBasic();
            http.formLogin();

            if (!authenticationEntryPointCustomizer.isEmpty()) {
                // assuming if there is no oauth2/jwt login, there won't be custom auth-entrypoints either
                log.warn("Missing authentication entrypoint for basic-auth and form-login");
            }
        }

        // authentication entrypoints, if there are customizers available
        if (!authenticationEntryPointCustomizer.isEmpty()) {
            http.exceptionHandling(spec -> {
                List<DelegateEntry> entryPoints = new ArrayList<>();
                authenticationEntryPointCustomizer.forEach(customizer -> customizer.customize(entryPoints));
                spec.authenticationEntryPoint(new DelegatingServerAuthenticationEntryPoint(entryPoints));
            });
        }

        // do we need to do anything special for logout ?
        http.logout(logout -> logout.logoutSuccessHandler(logoutSuccessHandler));

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
    public GlobalFilter proxyUpstreamUnavailableWebFilter() {
        return new ProxyUpstreamUnavailableWebFilter();
    }


    private static class OAuth2ResourceServerGuard {

        public static boolean shouldConfigure(Environment environment) {
            return environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri") != null;
        }
    }

}
