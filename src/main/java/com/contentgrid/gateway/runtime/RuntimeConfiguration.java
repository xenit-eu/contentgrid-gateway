package com.contentgrid.gateway.runtime;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_WEB_FILTER_CHAIN_FILTER_ORDER;

import com.contentgrid.configuration.api.ComposedConfiguration;
import com.contentgrid.configuration.api.fragments.ComposedConfigurationRepository;
import com.contentgrid.configuration.api.observable.Observable;
import com.contentgrid.configuration.applications.ApplicationConfiguration;
import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.ServiceDiscoveryProperties;
import com.contentgrid.gateway.runtime.actuate.ContentGridActuatorEndpoint;
import com.contentgrid.gateway.runtime.application.ContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.authorization.RuntimeOpaQueryProvider;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.config.kubernetes.KubernetesLabels;
import com.contentgrid.gateway.runtime.cors.RuntimeCorsConfigurationSource;
import com.contentgrid.gateway.runtime.routing.ApplicationIdRequestResolver;
import com.contentgrid.gateway.runtime.routing.CachingApplicationIdRequestResolver;
import com.contentgrid.gateway.runtime.routing.DefaultRuntimeRequestRouter;
import com.contentgrid.gateway.runtime.routing.DynamicVirtualHostApplicationIdResolver;
import com.contentgrid.gateway.runtime.routing.RuntimeDeploymentGatewayFilter;
import com.contentgrid.gateway.runtime.routing.RuntimeRequestRouter;
import com.contentgrid.gateway.runtime.routing.RuntimeServiceInstanceSelector;
import com.contentgrid.gateway.runtime.routing.SimpleRuntimeServiceInstanceSelector;
import com.contentgrid.gateway.runtime.servicediscovery.KubernetesServiceDiscovery;
import com.contentgrid.gateway.runtime.servicediscovery.ServiceDiscovery;
import com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter;
import com.contentgrid.gateway.runtime.web.ContentGridResponseHeadersWebFilter;
import com.contentgrid.gateway.security.jwt.issuer.JwtSignerRegistry;
import com.contentgrid.gateway.security.jwt.issuer.LocallyIssuedJwtGatewayFilterFactory;
import com.contentgrid.gateway.security.oidc.ReactiveClientRegistrationIdResolver;
import com.contentgrid.thunx.pdp.opa.OpaQueryProvider;
import com.contentgrid.thunx.spring.gateway.filter.AbacGatewayFilterFactory;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.Fabric8ServiceInstanceMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled")
@EnableConfigurationProperties(RuntimePlatformProperties.class)
public class RuntimeConfiguration {

    @Bean
    RuntimeDeploymentGatewayFilter deploymentGatewayFilter() {
        return new RuntimeDeploymentGatewayFilter();
    }

    @Bean
    RouteLocator runtimeRouteLocator(
            RouteLocatorBuilder builder,
            ApplicationIdRequestResolver applicationIdRequestResolver,
            AbacGatewayFilterFactory abacGatewayFilterFactory,
            TokenRelayGatewayFilterFactory tokenRelayGatewayFilterFactory,
            LocallyIssuedJwtGatewayFilterFactory locallyIssuedJwtGatewayFilterFactory,
            JwtSignerRegistry jwtSignerRegistry,
            RuntimePlatformProperties runtimePlatformProperties
    ) {

        var routes = builder.routes();

        runtimePlatformProperties.endpoints().forEach(endpoint -> {
            routes.route(endpoint.endpointId(), r -> r
                    .predicate(exchange -> applicationIdRequestResolver.resolveApplicationId(exchange).isPresent())
                    .and()
                    .path(endpoint.pathPattern())
                    .filters(f -> f
                            .preserveHostHeader()
                            .removeRequestHeader("Cookie")
                            .filter(locallyIssuedJwtGatewayFilterFactory.apply(c -> {
                                c.setSigner(endpoint.endpointId());
                                c.setClaimsResolver(endpoint.endpointId());
                            }))
                    )
                    .uri(endpoint.upstreamUri())
            );
        });

        GatewayFilter tokenFilter = jwtSignerRegistry.hasSigner("apps")?
                locallyIssuedJwtGatewayFilterFactory.apply(c -> {
                    c.setSigner("apps");
                    c.setClaimsResolver("apps");
                }) :
                tokenRelayGatewayFilterFactory.apply();

        routes.route(r -> r
                .predicate(exchange -> applicationIdRequestResolver.resolveApplicationId(exchange).isPresent())
                .filters(f -> f
                        .preserveHostHeader()
                        .removeRequestHeader("Cookie")
                        .filter(abacGatewayFilterFactory.apply(c -> {}))
                        .filter(tokenFilter)
                )
                .uri("cg://ignored")
        );

        return routes.build();
    }


    @Bean
    Customizer<ServerHttpSecurity.AuthorizeExchangeSpec> runtimeEndpointsAuthorizeExchangeCustomizer(
            RuntimePlatformProperties runtimePlatformProperties
    ) {
        return exchange -> {
            runtimePlatformProperties.endpoints().forEach(endpointDefinition -> {
                switch (endpointDefinition.authorizationType()) {
                    case PUBLIC -> exchange.pathMatchers(endpointDefinition.pathPattern()).permitAll();
                    case AUTHENTICATED -> exchange.pathMatchers(endpointDefinition.pathPattern()).authenticated();
                    case DEFAULT -> { /* Fallthrough to the default main authorization configuration */ }
                }
            });
        };
    }

    @Bean
    public ServiceCatalog serviceTracker(ContentGridDeploymentMetadata deploymentMetadata) {
        return new ServiceCatalog(deploymentMetadata);
    }

    @Bean
    public ContentGridActuatorEndpoint contentGridActuatorEndpoint(WebEndpointProperties endpointProperties,
            ApplicationConfigurationRepository applicationConfigurationRepository,
            ReactiveClientRegistrationIdResolver clientRegistrationIdResolver,
            ReactiveClientRegistrationRepository clientRegistrationRepository) {
        return new ContentGridActuatorEndpoint(endpointProperties, applicationConfigurationRepository,
                clientRegistrationIdResolver, clientRegistrationRepository);
    }

    @Bean
    ContentGridDeploymentMetadata deploymentMetadata() {
        return new SimpleContentGridDeploymentMetadata();
    }

    @Bean
    @Order(CONTENTGRID_WEB_FILTER_CHAIN_FILTER_ORDER)
    ContentGridAppRequestWebFilter contentGridAppRequestWebFilter(
            ContentGridDeploymentMetadata serviceMetadata,
            RuntimeRequestRouter requestRouter) {
        return new ContentGridAppRequestWebFilter(serviceMetadata, requestRouter);
    }

    @Bean
    @Order(CONTENTGRID_WEB_FILTER_CHAIN_FILTER_ORDER + 10)
    ContentGridResponseHeadersWebFilter contentGridResponseHeadersWebFilter() {
        return new ContentGridResponseHeadersWebFilter();
    }

    @Bean
    ApplicationIdRequestResolver virtualHostApplicationIdResolver(
            Observable<ComposedConfiguration<ApplicationId, ApplicationConfiguration>> configurations
    ) {
        var delegate = new DynamicVirtualHostApplicationIdResolver(configurations);
        return new CachingApplicationIdRequestResolver(delegate);
    }

    @Bean
    RuntimeServiceInstanceSelector simpleRuntimeServiceInstanceSelector(
            ContentGridDeploymentMetadata deploymentMetadata) {
        return new SimpleRuntimeServiceInstanceSelector(deploymentMetadata);
    }

    @Bean
    RuntimeRequestRouter requestRouter(ServiceCatalog serviceCatalog,
            ApplicationIdRequestResolver applicationIdRequestResolver,
            RuntimeServiceInstanceSelector serviceInstanceSelector) {
        return new DefaultRuntimeRequestRouter(serviceCatalog, applicationIdRequestResolver, serviceInstanceSelector);
    }

    @Bean
    CorsConfigurationSource runtimeCorsConfigurationSource(ApplicationIdRequestResolver applicationIdResolver,
            ApplicationConfigurationRepository appConfigRepository) {
        return new RuntimeCorsConfigurationSource(applicationIdResolver, appConfigRepository);
    }

    @Bean
    OpaQueryProvider<ServerWebExchange> opaQueryProvider(ServiceCatalog serviceCatalog,
            ContentGridDeploymentMetadata deploymentMetadata) {
        return new RuntimeOpaQueryProvider(serviceCatalog, deploymentMetadata);
    }

    @Bean
    ComposableApplicationConfigurationRepository applicationConfigurationRepository(
            ComposedConfigurationRepository<String, ApplicationId, ApplicationConfiguration> composedConfigurationRepository
    ) {
        return new ComposableApplicationConfigurationRepository(composedConfigurationRepository);
    }

    @Configuration
    @ConditionalOnProperty("servicediscovery.enabled")
    static class ServiceDiscoveryConfiguration {

        @Bean
        ApplicationRunner runner(ObjectProvider<ServiceDiscovery> serviceDiscovery) {
            return args -> serviceDiscovery.ifAvailable(ServiceDiscovery::discoverApis);
        }

        @ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
        static class KubernetesServiceDiscoveryConfiguration {

            public static final LabelSelector GATEWAY_LABEL_SELECTOR = new LabelSelectorBuilder()
                    .addToMatchLabels(KubernetesLabels.K8S_MANAGEDBY, "contentgrid")
                    .addToMatchLabels(KubernetesLabels.CONTENTGRID_SERVICETYPE, "gateway")
                    .build();

            @Bean
            ServiceDiscovery serviceDiscovery(ServiceDiscoveryProperties properties, KubernetesClient kubernetesClient,
                    ServiceCatalog serviceCatalog, Fabric8ServiceInstanceMapper instanceMapper) {
                log.info("Enabled k8s service discovery (namespace:{})", properties.getNamespace());
                return new KubernetesServiceDiscovery(kubernetesClient, properties.getNamespace(), properties.getResync(),
                        serviceCatalog, serviceCatalog, instanceMapper);
            }

        }
    }
}
