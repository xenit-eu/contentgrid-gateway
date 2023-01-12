package eu.xenit.alfred.content.gateway.servicediscovery;

import org.springframework.cloud.client.ServiceInstance;

public interface ServiceAddedHandler {
    void handleServiceAdded(ServiceInstance service);
}
