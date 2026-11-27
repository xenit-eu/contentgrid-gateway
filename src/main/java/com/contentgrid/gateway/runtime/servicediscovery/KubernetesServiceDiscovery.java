package com.contentgrid.gateway.runtime.servicediscovery;

import com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.config.kubernetes.KubernetesLabels;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointConditions;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.Fabric8ServiceInstanceMapper;

/**
 * Discovers api Services, and only offers a Service while it has at least one ready endpoint.
 * <p>
 * Readiness comes from the EndpointSlices of the Service. The EndpointSlice controller copies the Service labels onto
 * the EndpointSlices, so the same label selector is used for both.
 */
@Slf4j
@RequiredArgsConstructor
public class KubernetesServiceDiscovery implements ServiceDiscovery, AutoCloseable {

    private static final String SERVICE_NAME_LABEL = "kubernetes.io/service-name";
    private static final String BY_SERVICE_NAME = "by-service-name";

    private final KubernetesClient client;
    private final String namespace;
    private final Duration resyncInterval;

    private final ServiceAddedHandler serviceAddedHandler;
    private final ServiceDeletedHandler serviceDeletedHandler;

    private final Fabric8ServiceInstanceMapper mapper;

    private SharedIndexInformer<Service> informer;
    private SharedIndexInformer<EndpointSlice> endpointSliceInformer;

    private static final LabelSelector selector = new LabelSelectorBuilder()
            .addToMatchLabels(KubernetesLabels.CONTENTGRID_SERVICETYPE, "api")
            .build();

    // TODO this should by a bean-init method
    @Override
    public void discoverApis() {
        this.endpointSliceInformer = client.discovery().v1().endpointSlices()
                .inNamespace(namespace)
                .withLabelSelector(selector)
                .runnableInformer(resyncInterval.toMillis());
        this.endpointSliceInformer.addIndexers(Map.of(BY_SERVICE_NAME, slice ->
                Optional.ofNullable(slice.getMetadata().getLabels())
                        .map(labels -> labels.get(SERVICE_NAME_LABEL))
                        .map(List::of)
                        .orElse(List.of())));
        this.endpointSliceInformer.addEventHandler(handler(this::onEndpointSliceChanged));

        this.informer = client.services()
                .inNamespace(namespace)
                .withLabelSelector(selector)
                .runnableInformer(resyncInterval.toMillis());
        this.informer.addEventHandler(new ResourceEventHandler<Service>() {
            @Override
            public void onAdd(Service obj) {
                reconcile(obj.getMetadata().getName());
            }

            @Override
            public void onUpdate(Service oldObj, Service newObj) {
                if (resourceVersionChanged(oldObj, newObj)) {
                    reconcile(newObj.getMetadata().getName());
                }
            }

            @Override
            public void onDelete(Service obj, boolean deletedFinalStateUnknown) {
                remove(obj);
            }
        });

        // EndpointSlices are synced first, so known Services are offered as soon as they are discovered
        this.endpointSliceInformer.run();
        this.informer.run();
    }

    private void onEndpointSliceChanged(EndpointSlice slice) {
        var labels = slice.getMetadata().getLabels();
        if (labels != null && labels.get(SERVICE_NAME_LABEL) != null) {
            reconcile(labels.get(SERVICE_NAME_LABEL));
        }
    }

    /**
     * Offers the Service when it exists and has a ready endpoint, and withdraws it otherwise. Always looks at the
     * current state of both informer caches, so the outcome does not depend on the order in which events arrive.
     */
    private synchronized void reconcile(String serviceName) {
        var service = this.informer.getStore().getByKey(Cache.namespaceKeyFunc(namespace, serviceName));
        if (service == null) {
            return;
        }

        if (hasReadyEndpoint(serviceName)) {
            var instance = map(service);
            serviceAddedHandler.handleServiceAdded(instance);
            log.info("{} discovered", instance);
        } else {
            remove(service);
        }
    }

    private synchronized void remove(Service service) {
        var instance = map(service);
        serviceDeletedHandler.handleServiceDeleted(instance);
        log.info("{} removed", instance);
    }

    private boolean hasReadyEndpoint(String serviceName) {
        return this.endpointSliceInformer.getIndexer().byIndex(BY_SERVICE_NAME, serviceName).stream()
                .flatMap(slice -> Optional.ofNullable(slice.getEndpoints()).orElse(List.of()).stream())
                // a missing ready condition must be interpreted as ready, see the EndpointConditions API docs
                .map(endpoint -> Optional.ofNullable(endpoint.getConditions()).map(EndpointConditions::getReady))
                .anyMatch(ready -> ready.orElse(true));
    }

    private ServiceInstance map(Service service) {
        var instance = mapper.map(service);
        var metadata = new HashMap<>(instance.getMetadata());
        metadata.put(SimpleContentGridDeploymentMetadata.METADATA_CREATION_TIMESTAMP,
                service.getMetadata().getCreationTimestamp());
        return new DefaultServiceInstance(instance.getInstanceId(), instance.getServiceId(), instance.getHost(),
                instance.getPort(), instance.isSecure(), metadata);
    }

    private static boolean resourceVersionChanged(HasMetadata oldObj, HasMetadata newObj) {
        // Only trigger an update when the resource has actually changed
        return !Objects.equals(oldObj.getMetadata().getResourceVersion(), newObj.getMetadata().getResourceVersion());
    }

    private static <T extends HasMetadata> ResourceEventHandler<T> handler(Consumer<T> onChange) {
        return new ResourceEventHandler<>() {
            @Override
            public void onAdd(T obj) {
                onChange.accept(obj);
            }

            @Override
            public void onUpdate(T oldObj, T newObj) {
                if (resourceVersionChanged(oldObj, newObj)) {
                    onChange.accept(newObj);
                }
            }

            @Override
            public void onDelete(T obj, boolean deletedFinalStateUnknown) {
                onChange.accept(obj);
            }
        };
    }

    @Override
    public void close() throws Exception {
        if (this.informer != null) {
            this.informer.close();
        }
        if (this.endpointSliceInformer != null) {
            this.endpointSliceInformer.close();
        }
    }
}
