package com.contentgrid.gateway.runtime.config.kubernetes;

import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class KubernetesApplicationSecretsWatcher implements AutoCloseable {

    private final ComposableApplicationConfigurationRepository appConfigRepository;
    private final KubernetesClient client;
    private final String namespace;

    private final SecretMapper<Secret> secretMapper = new Fabric8SecretMapper();

    private static final LabelSelector selector = new LabelSelectorBuilder()
            .addToMatchLabels("app.kubernetes.io/managed-by", "contentgrid")
            .addToMatchLabels("app.contentgrid.com/service-type", "gateway")
            .build();
    private Watch watch;

    // TODO dispose watch
    public void watchSecrets() {
        this.watch = client.secrets()
                .inNamespace(namespace)
                .withLabelSelector(selector)
                .watch(new GatewaySecretWatcher());
    }

    @Override
    public void close() throws IOException {
        var closure = this.watch;
        if (closure != null) {
            this.watch = null;
            closure.close();
        }
    }


    private class GatewaySecretWatcher implements Watcher<Secret> {

        @Override
        public void eventReceived(Action action, Secret resource) {
            secretMapper.apply(resource).ifPresent(fragment -> {
                var appConfig = appConfigRepository.getOrDefault(fragment.getApplicationId());

                log.info("secret {} {} [app-id:{}] # kubectl get secrets {} -o yaml", action, resource.getMetadata().getName(), fragment.getApplicationId(), resource.getMetadata().getName());

                switch (action) {
                    case ADDED, MODIFIED -> {
                        appConfig = appConfig.withAdditionalConfiguration(fragment);
                        appConfigRepository.put(appConfig);
                    }
                    case DELETED -> {
                        appConfig = appConfig.withoutConfiguration(fragment.getConfigurationId());
                        if (appConfig.isEmpty()) {
                            appConfigRepository.remove(appConfig.getApplicationId());
                        } else {
                            appConfigRepository.put(appConfig);
                        }
                    }
                    default -> log.warn("Unknown action {} on secret {}", action, resource);
                }
            });
        }

        @Override
        public void onClose(WatcherException cause) {
            log.info("Closed secret watcher", cause);
        }
    }

}
