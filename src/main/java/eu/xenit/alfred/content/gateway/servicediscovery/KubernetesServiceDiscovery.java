package eu.xenit.alfred.content.gateway.servicediscovery;


import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class KubernetesServiceDiscovery implements ServiceDiscovery {
    private final KubernetesClient client;
    private final String namespace;

    private final ServiceAddedHandler serviceAddedHandler;
    private final ServiceDeletedHandler serviceDeletedHandler;

    private static final LabelSelector selector = new LabelSelectorBuilder()
            .addToMatchLabels("app.kubernetes.io/managed-by", "contentgrid")
            .addToMatchLabels("app.contentgrid.com/service-type", "api")
            .build();

    @Override
    public void discoverApis() {
        client.services().inNamespace(namespace).withLabelSelector(selector).watch(new Watcher<Service>() {
            @Override
            public void eventReceived(Action action, Service resource) {
                AppService appService = fromMetadata(resource.getMetadata());
                if (action == Action.ADDED || action == Action.MODIFIED) {
                    serviceAddedHandler.handleServiceAdded(appService);
                    log.info("{} discovered", appService);
                } else if (action == Action.DELETED) {
                    serviceDeletedHandler.handleServiceDeleted(appService);
                    log.info("{} deleted", appService);
                }
            }

            @Override
            public void onClose(WatcherException cause) {

            }
        });

    }

    private static AppService fromMetadata(ObjectMeta meta) {
        return new AppService(
                meta.getName(),
                meta.getLabels().get("app.contentgrid.com/app-id"),
                meta.getLabels().get("app.contentgrid.com/deployment-request-id"),
                meta.getAnnotations() == null ? null : meta.getAnnotations().get("authz.contentgrid.com/policy-package"),
                meta.getNamespace()
        );
    }
}
