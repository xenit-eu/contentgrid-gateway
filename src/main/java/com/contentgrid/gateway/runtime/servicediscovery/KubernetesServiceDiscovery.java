package com.contentgrid.gateway.runtime.servicediscovery;


import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import java.io.Closeable;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.Fabric8ServiceInstanceMapper;

@Slf4j
@RequiredArgsConstructor
public class KubernetesServiceDiscovery implements ServiceDiscovery {

    private final KubernetesClient client;
    private final String namespace;
    private final long resyncInterval;

    private final ServiceAddedHandler serviceAddedHandler;
    private final ServiceDeletedHandler serviceDeletedHandler;

    private final Fabric8ServiceInstanceMapper mapper;


    private static final LabelSelector selector = new LabelSelectorBuilder()
            .addToMatchLabels("app.kubernetes.io/managed-by", "contentgrid")
            .addToMatchLabels("app.contentgrid.com/service-type", "api")
            .build();

    // TODO this should by a bean-init method
    @Override
    public void discoverApis() {
        client.services()
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
                        this.onAdd(newObj);
                    }

                    @Override
                    public void onDelete(Service obj, boolean deletedFinalStateUnknown) {
                        var service = mapper.map(obj);
                        serviceDeletedHandler.handleServiceDeleted(service);
                        log.info("{} deleted", service);
                    }
                }, resyncInterval * 1000);
    }
}
