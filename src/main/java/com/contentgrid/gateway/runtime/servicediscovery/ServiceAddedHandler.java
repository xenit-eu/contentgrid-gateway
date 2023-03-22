package com.contentgrid.gateway.runtime.servicediscovery;

import org.springframework.cloud.client.ServiceInstance;

public interface ServiceAddedHandler {
    void handleServiceAdded(ServiceInstance service);
}
