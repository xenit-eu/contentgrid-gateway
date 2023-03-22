package com.contentgrid.gateway.runtime;

import static com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter.CONTENTGRID_WEB_FILTER_CHAIN_FILTER_ORDER;

import com.contentgrid.gateway.ServiceDiscoveryProperties;
import com.contentgrid.gateway.runtime.application.SimpleContentGridApplicationMetadata;
import com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.routing.SimpleContentGridRequestRouter;
import com.contentgrid.gateway.runtime.web.ContentGridAppRequestWebFilter;
import com.contentgrid.gateway.runtime.web.ContentGridResponseHeadersWebFilter;
import com.contentgrid.gateway.runtime.application.ServiceTracker;
import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.config.kubernetes.Fabric8ConfigMapMapper;
import com.contentgrid.gateway.runtime.config.kubernetes.Fabric8SecretMapper;
import com.contentgrid.gateway.runtime.config.kubernetes.KubernetesResourceWatcherBinding;
import com.contentgrid.gateway.runtime.application.ContentGridApplicationMetadata;
import com.contentgrid.gateway.runtime.application.ContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.servicediscovery.KubernetesServiceDiscovery;
import com.contentgrid.gateway.runtime.servicediscovery.ServiceDiscovery;
import com.contentgrid.thunx.pdp.opa.OpaQueryProvider;
import io.fabric8.kubernetes.client.KubernetesClient;
import com.contentgrid.gateway.runtime.routing.ContentGridRequestRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.Fabric8ServiceInstanceMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "contentgrid.gateway.runtime-platform.enabled")
public class RuntimeConfiguration {


    @Bean
    public ServiceTracker serviceTracker(ApplicationEventPublisher publisher,
            ContentGridApplicationMetadata applicationMetadata) {
        return new ServiceTracker(publisher, applicationMetadata);
    }

    @Bean
    ContentGridDeploymentMetadata deploymentMetadata() {
        return new SimpleContentGridDeploymentMetadata();
    }

    @Bean
    ContentGridApplicationMetadata applicationMetadata(ContentGridDeploymentMetadata deploymentMetadata) {
        return new SimpleContentGridApplicationMetadata(deploymentMetadata);
    }

    @Bean
    @Order(CONTENTGRID_WEB_FILTER_CHAIN_FILTER_ORDER)
    ContentGridAppRequestWebFilter contentGridAppRequestWebFilter(
            ContentGridDeploymentMetadata serviceMetadata,
            ContentGridRequestRouter requestRouter) {
        return new ContentGridAppRequestWebFilter(serviceMetadata, requestRouter);
    }

    @Bean
    @Order(CONTENTGRID_WEB_FILTER_CHAIN_FILTER_ORDER + 10)
    ContentGridResponseHeadersWebFilter contentGridResponseHeadersWebFilter() {
        return new ContentGridResponseHeadersWebFilter();
    }

    @Bean
    ContentGridRequestRouter requestRouter(ServiceTracker serviceTracker,
            ContentGridApplicationMetadata applicationMetadata,
            ContentGridDeploymentMetadata serviceMetadata) {
        return new SimpleContentGridRequestRouter(serviceTracker, applicationMetadata, serviceMetadata);
    }

    @Bean
    ComposableApplicationConfigurationRepository applicationConfigurationRepository() {
        return new ComposableApplicationConfigurationRepository();
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
                    ServiceTracker serviceTracker, Fabric8ServiceInstanceMapper instanceMapper) {
                log.info("Enabled k8s service discovery (namespace:{})", properties.getNamespace());
                return new KubernetesServiceDiscovery(kubernetesClient, properties.getNamespace(), serviceTracker,
                        serviceTracker, instanceMapper);
            }

            @Bean
            KubernetesResourceWatcherBinding kubernetesApplicationConfigMapWatcher(
                    ComposableApplicationConfigurationRepository appConfigRepository,
                    KubernetesClient kubernetesClient, ServiceDiscoveryProperties properties) {
                return new KubernetesResourceWatcherBinding(appConfigRepository, kubernetesClient,
                        properties.getNamespace());
            }

            @Bean
            ApplicationRunner k8sWatchSecrets(KubernetesResourceWatcherBinding watcherBinding) {
                return args -> watcherBinding.watch(KubernetesClient::secrets, new Fabric8SecretMapper());
            }

            @Bean
            ApplicationRunner k8sWatchConfigMaps(KubernetesResourceWatcherBinding watcherBinding) {
                return args -> watcherBinding.watch(KubernetesClient::configMaps, new Fabric8ConfigMapMapper());
            }

        }
    }


}
