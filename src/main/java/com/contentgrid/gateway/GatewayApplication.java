package com.contentgrid.gateway;

import com.contentgrid.gateway.cors.CorsConfigurationResolver;
import com.contentgrid.gateway.cors.CorsResolverProperties;
import com.contentgrid.gateway.error.ProxyUpstreamUnavailableWebFilter;
import com.contentgrid.gateway.runtime.authorization.RuntimeOpaInputProvider;
import com.contentgrid.gateway.security.authority.Actor;
import com.contentgrid.gateway.security.authority.Actor.ActorType;
import com.contentgrid.gateway.security.authority.PrincipalAuthenticationDetailsGrantedAuthority;
import com.contentgrid.gateway.security.jwt.issuer.actuate.JWKSetEndpoint;
import com.contentgrid.gateway.security.refresh.AuthenticationRefresher;
import com.contentgrid.gateway.security.refresh.AuthenticationRefreshingServerSecurityContextRepository;
import com.contentgrid.thunx.pdp.opa.OpaInputProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.reactive.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authorization.AuthenticatedReactiveAuthorizationManager;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2LoginSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2ResourceServerSpec;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint.DelegateEntry;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootApplication
@EnableConfigurationProperties({ CorsResolverProperties.class, ServiceDiscoveryProperties.class})
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
                    var claims = mapper.readValue(authorities, typeRef);
                    claims.putIfAbsent(JwtClaimNames.SUB, username);
                    claims.putIfAbsent(StandardClaimNames.NAME, username);
                    return User
                            .withUsername(username)
                            .password(NOOP_PASSWORD + username)
                            .authorities(
                                    new PrincipalAuthenticationDetailsGrantedAuthority(new Actor(
                                            ActorType.USER,
                                            () -> claims,
                                            null
                                    ))
                            )
                            .build();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
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
    @ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled", havingValue = "false", matchIfMissing = true)
    public CorsConfigurationSource corsConfigurationSource(CorsResolverProperties corsResolverProperties) {
        return new CorsConfigurationResolver(corsResolverProperties);
    }

    @Bean
    Customizer<ServerHttpSecurity.AuthorizeExchangeSpec> actuatorsAuthorizeExchangeCustomizer() {
        return exchange -> {
            // requests to the actuators /info, /health, /metrics, and /prometheus are allowed unauthenticated
            exchange.matchers(EndpointRequest.to(
                            InfoEndpoint.class,
                            HealthEndpoint.class,
                            MetricsEndpoint.class,
                            PrometheusScrapeEndpoint.class,
                            JWKSetEndpoint.class
                    )).permitAll()

                    // requests FROM localhost to actuator endpoints are all permitted
                    .matchers(new AndServerWebExchangeMatcher(
                            EndpointRequest.toAnyEndpoint(),
                            mgmtExchange -> {
                                var remoteAddress = mgmtExchange.getRequest().getRemoteAddress();
                                if (remoteAddress != null && remoteAddress.getAddress().isLoopbackAddress()) {
                                    return MatchResult.match();
                                }
                                return MatchResult.notMatch();
                            })
                    ).permitAll();
        };
    }

    @Bean
    public SecurityWebFilterChain springWebFilterChain(
            ServerHttpSecurity http,
            ObjectProvider<ReactiveAuthorizationManager<AuthorizationContext>> authorizationManager,
            ServerLogoutSuccessHandler logoutSuccessHandler,
            CorsConfigurationSource corsConfig,
            List<Customizer<OAuth2LoginSpec>> oauth2loginCustomizer,
            List<Customizer<OAuth2ResourceServerSpec>> oauth2resourceServerCustomizer,
            List<Customizer<List<DelegateEntry>>> authenticationEntryPointCustomizer,
            List<Customizer<ServerHttpSecurity.AuthorizeExchangeSpec>> authorizeCustomizer,
            AuthenticationRefresher authenticationRefresher,
            Optional<ReactiveAuthenticationManager> reactiveAuthenticationManagerOptional,
            Optional<ReactiveUserDetailsService> reactiveUserDetailsServiceOptional
    ) {
        http.authorizeExchange(exchange -> {
            authorizeCustomizer.forEach(customizer -> customizer.customize(exchange));

            exchange
                    // other requests must pass through the authorizationManager (opa/keycloak)
                    .anyExchange().access(authorizationManager.getIfAvailable(() -> {
                        log.warn("OpenPolicyAgent not configured, authorization disabled");
                        return AuthenticatedReactiveAuthorizationManager.authenticated();
                    }));
        });

        // Bearer token auth
        oauth2resourceServerCustomizer.forEach(http::oauth2ResourceServer);

        // OAuth2 login
        oauth2loginCustomizer.forEach(http::oauth2Login);

        // if there are no authentication customizers, fallback to http-basic
        if (oauth2resourceServerCustomizer.isEmpty() && oauth2loginCustomizer.isEmpty()) {
            http.httpBasic(Customizer.withDefaults());
            http.formLogin(Customizer.withDefaults());

            if (!authenticationEntryPointCustomizer.isEmpty()) {
                // assuming if there is no oauth2/jwt login, there won't be custom auth-entrypoints either
                log.warn("Missing authentication entrypoint for basic-auth and form-login");
            }
        }

        // authentication entry-points, if there are customizers available
        if (!authenticationEntryPointCustomizer.isEmpty()) {
            http.exceptionHandling(spec -> {
                List<DelegateEntry> entryPoints = new ArrayList<>();
                authenticationEntryPointCustomizer.forEach(customizer -> customizer.customize(entryPoints));
                spec.authenticationEntryPoint(new DelegatingServerAuthenticationEntryPoint(entryPoints));
            });
        }


        http.securityContextRepository(new AuthenticationRefreshingServerSecurityContextRepository(
                new WebSessionServerSecurityContextRepository(),
                authenticationRefresher
        ));

        // do we need to do anything special for logout ?
        http.logout(logout -> logout.logoutSuccessHandler(logoutSuccessHandler));

        http.cors(cors -> cors.configurationSource(corsConfig));

        http.csrf(CsrfSpec::disable);
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.mode(Mode.SAMEORIGIN)));

        if (reactiveUserDetailsServiceOptional.isEmpty() && reactiveAuthenticationManagerOptional.isEmpty()) {
            http.authenticationManager((authentication) -> Mono.error(new UsernameNotFoundException(authentication.getName())));
        }

        return http.build();
    }

    @Bean
    public GlobalFilter proxyUpstreamUnavailableWebFilter() {
        return new ProxyUpstreamUnavailableWebFilter();
    }

    @Bean
    OpaInputProvider<Authentication, ServerWebExchange> opaInputProvider() {
        return new RuntimeOpaInputProvider();
    }
}
