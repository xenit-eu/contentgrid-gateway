package com.contentgrid.gateway.runtime.servicediscovery;

import com.contentgrid.gateway.runtime.config.kubernetes.KubernetesLabels;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import java.time.Duration;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.Fabric8ServiceInstanceMapper;

@Slf4j
@RequiredArgsConstructor
public class KubernetesServiceDiscovery implements ServiceDiscovery, AutoCloseable {

    private final KubernetesClient client;
    private final String namespace;
    private final Duration resyncInterval;

    private final ServiceAddedHandler serviceAddedHandler;
    private final ServiceDeletedHandler serviceDeletedHandler;

    private final Fabric8ServiceInstanceMapper mapper;

    private SharedIndexInformer<Service> informer;

    private static final LabelSelector selector = new LabelSelectorBuilder()
            .addToMatchLabels(KubernetesLabels.CONTENTGRID_SERVICETYPE, "api")
            .build();

    // TODO this should by a bean-init method
    @Override
    public void discoverApis() {
        this.informer = client.services()
                .inNamespace(namespace)
                .withLabelSelector(selector)
                .inform(new ResourceEventHandler<Service>() {
                    @Override
                    public void onAdd(Service obj) {
                        var service = mapper.map(obj);
                        serviceAddedHandler.handleServiceAdded(service);
                        log.info("{} discovered", service);
                    }

                    @Override
                    public void onUpdate(Service oldObj, Service newObj) {
                        // Only trigger an update when the service has actually changed
                        if(!Objects.equals(oldObj.getMetadata().getResourceVersion(), newObj.getMetadata().getResourceVersion())) {
                            this.onAdd(newObj);
                        }
                    }

                    @Override
                    public void onDelete(Service obj, boolean deletedFinalStateUnknown) {
                        var service = mapper.map(obj);
                        serviceDeletedHandler.handleServiceDeleted(service);
                        log.info("{} deleted", service);
                    }
                }, resyncInterval.toMillis());
    }

    @Override
    public void close() throws Exception {
        if(this.informer != null) {
            this.informer.close();
        }
    }
}
