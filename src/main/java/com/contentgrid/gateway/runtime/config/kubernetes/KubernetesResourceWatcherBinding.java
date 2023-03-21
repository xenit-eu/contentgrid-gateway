package com.contentgrid.gateway.runtime.config.kubernetes;

import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class KubernetesResourceWatcherBinding implements AutoCloseable {

    private final ComposableApplicationConfigurationRepository appConfigRepository;
    private final KubernetesClient client;
    private final String namespace;

    private static final LabelSelector selector = new LabelSelectorBuilder()
            .addToMatchLabels(KubernetesLabels.K8S_MANAGEDBY, "contentgrid")
            .addToMatchLabels(KubernetesLabels.CONTENTGRID_SERVICETYPE, "gateway")
            .build();

    private final Set<Closeable> watches = new HashSet<>();

    public <T extends HasMetadata> void watch(Function<KubernetesClient, MixedOperation<T, ?, ?>> resourceSelector,
            KubernetesResourceMapper<T> mapper) {

        var watch = resourceSelector.apply(client)
                .inNamespace(namespace)
                .withLabelSelector(selector)
                .watch(new ApplicationConfigResourceWatcher<>(this.appConfigRepository, mapper));
        this.watches.add(watch);
    }

    @Override
    public void close() {
        var closables = Set.copyOf(this.watches);
        this.watches.clear();

        for (var closable : closables) {
            try {
                if (closable != null) {
                    closable.close();
                }
            } catch (IOException e) {
                log.warn("Closing watch failed", e);
            }
        }
    }

    @Slf4j
    @RequiredArgsConstructor
    static class ApplicationConfigResourceWatcher<T extends HasMetadata> implements Watcher<T> {

        private final ComposableApplicationConfigurationRepository appConfigRepository;

        private final KubernetesResourceMapper<T> resourceMapper;

        @Override
        public void eventReceived(Action action, T resource) {
            resourceMapper.apply(resource).ifPresent(fragment -> {
                log.info("{} {} {} [app-id:{}]", action, resource.getKind().toLowerCase(),
                        resource.getMetadata().getName(), fragment.getApplicationId());

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
