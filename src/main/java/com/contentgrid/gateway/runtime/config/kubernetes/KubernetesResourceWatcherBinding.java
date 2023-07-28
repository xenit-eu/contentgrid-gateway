package com.contentgrid.gateway.runtime.config.kubernetes;

import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import java.util.HashSet;
import java.util.Objects;
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

    private final Set<AutoCloseable> closeables = new HashSet<>();

    public <T extends HasMetadata> void inform(Function<KubernetesClient, MixedOperation<T, ?, ?>> resourceSelector,
            KubernetesResourceMapper<T> mapper) {

        var informer = resourceSelector.apply(client)
                .inNamespace(namespace)
                .withLabelSelector(selector)
                .inform(new ApplicationConfigResourceHandler<>(this.appConfigRepository, mapper));
        this.closeables.add(informer);
    }

    @Override
    public void close() {
        var closables = Set.copyOf(this.closeables);
        this.closeables.clear();

        for (var closable : closables) {
            try {
                if (closable != null) {
                    closable.close();
                }
            } catch (Exception e) {
                log.warn("Closing watch failed", e);
            }
        }
    }

    @Slf4j
    @RequiredArgsConstructor
    static class ApplicationConfigResourceHandler<T extends HasMetadata> implements ResourceEventHandler<T> {

        private final ComposableApplicationConfigurationRepository appConfigRepository;

        private final KubernetesResourceMapper<T> resourceMapper;

        @Override
        public void onNothing() {
            ResourceEventHandler.super.onNothing();
        }

        @Override
        public void onAdd(T resource) {
            resourceMapper.apply(resource).ifPresent(fragment -> {
                log.info("informer: on-add {} {} [app-id:{}]",
                        resource.getKind().toLowerCase(),
                        resource.getMetadata().getName(),
                        fragment.getApplicationId());

                appConfigRepository.merge(fragment);
            });
        }

        @Override
        public void onUpdate(T oldResource, T newResource) {
            var oldFragment = resourceMapper.apply(oldResource);
            var newFragment = resourceMapper.apply(newResource);

            if (Objects.equals(oldFragment, newFragment)) {
                log.info("informer: on-update {} {} - data has not changed, skipping",
                        newResource.getKind().toLowerCase(),
                        newResource.getMetadata().getName());

                return;
            }

            newFragment.ifPresent(fragment -> {
                log.info("informer: on-update {} {} [app-id:{}]",
                        newResource.getKind().toLowerCase(),
                        newResource.getMetadata().getName(),
                        fragment.getApplicationId());
                appConfigRepository.merge(fragment);
            });
        }

        @Override
        public void onDelete(T resource, boolean deletedFinalStateUnknown) {
            resourceMapper.apply(resource).ifPresent(fragment -> {

                if (deletedFinalStateUnknown) {
                    log.warn("MISSED DELETE EVENT - informer: on-delete {} {} [app-id:{}]",
                            resource.getKind().toLowerCase(),
                            resource.getMetadata().getName(),
                            fragment.getApplicationId());
                } else {
                    log.info("informer: on-delete {} {} [app-id:{}]",
                            resource.getKind().toLowerCase(),
                            resource.getMetadata().getName(),
                            fragment.getApplicationId());
                }
                appConfigRepository.revoke(fragment);
            });
        }
    }
}
