package com.contentgrid.gateway.servicediscovery;

import org.springframework.cloud.client.ServiceInstance;

public interface ServiceAddedHandler {
    void handleServiceAdded(ServiceInstance service);
}
