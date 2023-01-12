package eu.xenit.alfred.content.gateway.servicediscovery;


import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import java.io.Closeable;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.Fabric8ServiceInstanceMapper;

@Slf4j
@RequiredArgsConstructor
public class KubernetesServiceDiscovery implements ServiceDiscovery, Closeable {

    private final KubernetesClient client;
    private final String namespace;

    private final ServiceAddedHandler serviceAddedHandler;
    private final ServiceDeletedHandler serviceDeletedHandler;

    private final Fabric8ServiceInstanceMapper mapper;

    private Closeable watch = null;

    private static final LabelSelector selector = new LabelSelectorBuilder()
            .addToMatchLabels("app.kubernetes.io/managed-by", "contentgrid")
            .addToMatchLabels("app.contentgrid.com/service-type", "api")
            .build();

    // TODO this should by a bean-init method
    @Override
    public void discoverApis() {
        this.watch = client.services()
                .inNamespace(namespace)
                .withLabelSelector(selector)
                .watch(new Watcher<Service>() {
            @Override
            public void eventReceived(Action action, Service resource) {
                ServiceInstance service = mapper.map(resource);
                if (service == null) {
                    log.warn("Service (action:{}) {} not mapped", action, resource);
                    return;
                }

                if (action == Action.ADDED || action == Action.MODIFIED) {
                    serviceAddedHandler.handleServiceAdded(service);
                    log.info("{} discovered", service);
                } else if (action == Action.DELETED) {
                    serviceDeletedHandler.handleServiceDeleted(service);
                    log.info("{} deleted", service);
                } else {
                    log.warn("Action {} ignored on {}", action, service);
                }
            }

            @Override
            public void onClose(WatcherException cause) {

            }
        });
    }

    @Override
    public void close() throws IOException {
        var closure = this.watch;
        if (closure != null) {
            closure.close();
            this.watch = null;
        }
    }
}
