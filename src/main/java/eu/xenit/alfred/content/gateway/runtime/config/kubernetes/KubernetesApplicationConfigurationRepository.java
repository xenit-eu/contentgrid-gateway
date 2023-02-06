package eu.xenit.alfred.content.gateway.runtime.config.kubernetes;

import eu.xenit.alfred.content.gateway.collections.ObservableMap;
import eu.xenit.alfred.content.gateway.collections.ObservableMap.MapUpdate;
import eu.xenit.alfred.content.gateway.runtime.config.ApplicationConfigurationRepository;
import eu.xenit.alfred.content.gateway.runtime.ApplicationId;
import eu.xenit.alfred.content.gateway.runtime.config.ComposedApplicationConfiguration;
import eu.xenit.alfred.content.gateway.runtime.config.ApplicationConfiguration;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class KubernetesApplicationConfigurationRepository implements ApplicationConfigurationRepository {

    private final KubernetesClient client;

    private final String namespace;

    private final ObservableMap<ApplicationId, ComposedApplicationConfiguration> configs = new ObservableMap<>();

    private final SecretMapper<Secret> secretMapper = new Fabric8SecretMapper();

    private static final LabelSelector selector = new LabelSelectorBuilder()
            .addToMatchLabels("app.kubernetes.io/managed-by", "contentgrid")
            .addToMatchLabels("app.contentgrid.com/service-type", "gateway")
            .build();

    @Override
    public Mono<ApplicationConfiguration> getApplicationConfiguration(@NonNull ApplicationId appId) {
        return Mono.fromSupplier(() -> configs.get(appId));
    }

    @Override
    public Flux<MapUpdate<ApplicationId, ApplicationConfiguration>> observe() {
        return this.configs.observe()
                .map(update -> new MapUpdate<>(update.getType(), update.getKey(), update.getValue()));
    }

    public void watchSecrets() {
        client.secrets()
                .inNamespace(namespace)
                .withLabelSelector(selector)
                .watch(new GatewaySecretWatcher());
    }


    private class GatewaySecretWatcher implements Watcher<Secret> {

        @Override
        public void eventReceived(Action action, Secret resource) {
            secretMapper.apply(resource).ifPresent(fragment -> {
                var appConfig = configs.get(fragment.getApplicationId());
                if (appConfig == null) {
                    appConfig = new ComposedApplicationConfiguration(fragment.getApplicationId());
                }

                log.info("secret {} {} [app-id:{}] # kubectl get secrets {} -o yaml", action, resource.getMetadata().getName(), fragment.getApplicationId(), resource.getMetadata().getName());

                switch (action) {
                    case ADDED, MODIFIED -> {
                        appConfig = appConfig.withAdditionalConfiguration(fragment);
                        configs.put(appConfig.getApplicationId(), appConfig);
                    }
                    case DELETED -> {
                        appConfig = appConfig.withoutConfiguration(fragment.getConfigurationId());
                        if (appConfig.isEmpty()) {
                            configs.remove(appConfig.getApplicationId());
                        } else {
                            configs.put(appConfig.getApplicationId(), appConfig);
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
