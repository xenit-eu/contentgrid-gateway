package com.contentgrid.gateway.runtime;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_WEB_FILTER_CHAIN_FILTER_ORDER;

import com.contentgrid.gateway.ServiceDiscoveryProperties;
import com.contentgrid.gateway.runtime.application.ContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.application.ServiceCatalog;
import com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.config.kubernetes.Fabric8ConfigMapMapper;
import com.contentgrid.gateway.runtime.config.kubernetes.Fabric8SecretMapper;
import com.contentgrid.gateway.runtime.config.kubernetes.KubernetesResourceWatcherBinding;
import com.contentgrid.gateway.runtime.cors.RuntimeCorsConfigurationSource;
import com.contentgrid.gateway.runtime.routing.ApplicationIdRequestResolver;
import com.contentgrid.gateway.runtime.routing.RuntimeDeploymentGatewayFilter;
import com.contentgrid.gateway.runtime.routing.DefaultRuntimeRequestResolver;
import com.contentgrid.gateway.runtime.routing.DefaultRuntimeRequestRouter;
import com.contentgrid.gateway.runtime.routing.DynamicVirtualHostResolver;
import com.contentgrid.gateway.runtime.routing.DynamicVirtualHostResolver.ApplicationDomainNameEvent;
import com.contentgrid.gateway.runtime.routing.RuntimeRequestResolver;
import com.contentgrid.gateway.runtime.routing.RuntimeRequestRouter;
import com.contentgrid.gateway.runtime.routing.RuntimeServiceInstanceSelector;
import com.contentgrid.gateway.runtime.routing.RuntimeVirtualHostResolver;
import com.contentgrid.gateway.runtime.routing.SimpleRuntimeServiceInstanceSelector;
import com.contentgrid.gateway.runtime.servicediscovery.KubernetesServiceDiscovery;
import com.contentgrid.gateway.runtime.servicediscovery.ServiceDiscovery;
import com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter;
import com.contentgrid.gateway.runtime.web.ContentGridResponseHeadersWebFilter;
import com.contentgrid.thunx.pdp.opa.OpaQueryProvider;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.Fabric8ServiceInstanceMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled")
public class RuntimeConfiguration {
    @Bean
    RuntimeDeploymentGatewayFilter deploymentGatewayFilter() {
        return new RuntimeDeploymentGatewayFilter();
    }

    @Bean
    RouteLocator runtimeAppRouteLocator(RouteLocatorBuilder builder, RuntimeRequestResolver requestResolver) {
        return builder.routes()
                .route(r -> r
                        .predicate(exchange -> requestResolver.resolveDeploymentId(exchange).isPresent())
                        .filters(GatewayFilterSpec::preserveHostHeader)
                        .uri("cg://ignored")
                )
                .build();
    }

    @Bean
    public ServiceCatalog serviceTracker(ContentGridDeploymentMetadata deploymentMetadata) {
        return new ServiceCatalog(deploymentMetadata);
    }

    @Bean
    ContentGridDeploymentMetadata deploymentMetadata() {
        return new SimpleContentGridDeploymentMetadata();
    }

    @Bean
    RuntimeRequestResolver runtimeRequestResolver() {
        return new DefaultRuntimeRequestResolver();
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
    RuntimeVirtualHostResolver runtimeVirtualHostResolver(ApplicationConfigurationRepository appConfigRepository,
            ApplicationEventPublisher eventPublisher) {
        return new DynamicVirtualHostResolver(appConfigRepository.observe()
                .map(update -> switch (update.getType()) {
                            case PUT -> ApplicationDomainNameEvent.put(update.getKey(), update.getValue().getDomains());
                            case REMOVE -> ApplicationDomainNameEvent.delete(update.getKey());
                            case CLEAR -> ApplicationDomainNameEvent.clear();
                        }), eventPublisher);
    }

    @Bean
    RuntimeServiceInstanceSelector simpleRuntimeServiceInstanceSelector(ContentGridDeploymentMetadata deploymentMetadata) {
        return new SimpleRuntimeServiceInstanceSelector(deploymentMetadata);
    }

    @Bean
    RuntimeRequestRouter requestRouter(ServiceCatalog serviceCatalog,
            RuntimeVirtualHostResolver runtimeVirtualHostResolver,
            RuntimeServiceInstanceSelector serviceInstanceSelector) {
        return new DefaultRuntimeRequestRouter(serviceCatalog, runtimeVirtualHostResolver, serviceInstanceSelector);
    }

    @Bean
    ComposableApplicationConfigurationRepository applicationConfigurationRepository() {
        return new ComposableApplicationConfigurationRepository();
    }

    @Bean
    CorsConfigurationSource runtimeCorsConfigurationSource(ApplicationIdRequestResolver applicationIdResolver,
            ApplicationConfigurationRepository appConfigRepository) {
        return new RuntimeCorsConfigurationSource(applicationIdResolver, appConfigRepository);
    }

    @Bean
    OpaQueryProvider<ServerWebExchange> opaQueryProvider(ServiceCatalog serviceCatalog, ContentGridDeploymentMetadata deploymentMetadata) {
        return new RuntimeOpaQueryProvider(serviceCatalog, deploymentMetadata);
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

            @Bean
            ServiceDiscovery serviceDiscovery(ServiceDiscoveryProperties properties, KubernetesClient kubernetesClient,
                    ServiceCatalog serviceCatalog, Fabric8ServiceInstanceMapper instanceMapper) {
                log.info("Enabled k8s service discovery (namespace:{})", properties.getNamespace());
                return new KubernetesServiceDiscovery(kubernetesClient, properties.getNamespace(), serviceCatalog,
                        serviceCatalog, instanceMapper);
            }

            @Bean
            KubernetesResourceWatcherBinding kubernetesApplicationConfigMapWatcher(
                    ComposableApplicationConfigurationRepository appConfigRepository,
                    KubernetesClient kubernetesClient, ServiceDiscoveryProperties properties) {
                return new KubernetesResourceWatcherBinding(appConfigRepository, kubernetesClient,
                        properties.getNamespace(), properties.getResync());
            }

            @Bean
            ApplicationRunner k8sWatchSecrets(KubernetesResourceWatcherBinding watcherBinding) {
                return args -> watcherBinding.inform(KubernetesClient::secrets, new Fabric8SecretMapper());
            }

            @Bean
            ApplicationRunner k8sWatchConfigMaps(KubernetesResourceWatcherBinding watcherBinding) {
                return args -> watcherBinding.inform(KubernetesClient::configMaps, new Fabric8ConfigMapMapper());
            }

        }
    }
}
