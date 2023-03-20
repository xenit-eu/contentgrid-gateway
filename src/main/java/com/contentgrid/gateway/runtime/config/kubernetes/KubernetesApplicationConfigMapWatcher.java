package com.contentgrid.gateway.runtime.config.kubernetes;

import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class KubernetesApplicationConfigMapWatcher implements AutoCloseable {

    private final ComposableApplicationConfigurationRepository appConfigRepository;
    private final KubernetesClient client;
    private final String namespace;

    private static final LabelSelector selector = new LabelSelectorBuilder()
            .addToMatchLabels(KubernetesLabels.K8S_MANAGEDBY, "contentgrid")
            .addToMatchLabels(KubernetesLabels.CONTENTGRID_SERVICETYPE, "gateway")
            .build();
    private Watch watch;

    public void watchConfigMaps() {
        this.watch = client.configMaps()
                .inNamespace(namespace)
                .withLabelSelector(selector)
                .watch(new GatewayConfigMapWatcher(this.appConfigRepository));
    }

    @Override
    public void close() {
        var closure = this.watch;
        if (closure != null) {
            this.watch = null;
            closure.close();
        }
    }

    @RequiredArgsConstructor
    static class GatewayConfigMapWatcher implements Watcher<ConfigMap> {

        private final ComposableApplicationConfigurationRepository appConfigRepository;

        private final ApplicationConfigurationMapper<ConfigMap> resourceMapper = new Fabric8ConfigMapMapper();

        @Override
        public void eventReceived(Action action, ConfigMap resource) {
            resourceMapper.apply(resource).ifPresent(fragment -> {
                log.info("configmap {} {} [app-id:{}] # kubectl get configmap {} -o yaml",
                        action, resource.getMetadata().getName(), fragment.getApplicationId(),
                        resource.getMetadata().getName());

                switch (action) {
                    case ADDED, MODIFIED -> appConfigRepository.merge(fragment);
                    case DELETED -> appConfigRepository.revoke(fragment);
                    default -> log.warn("Unknown action {} on secret {}", action, resource);
                }
            });
        }

        @Override
        public void onClose(WatcherException cause) {
            log.info("Closed configmap watcher", cause);
        }
    }

}
