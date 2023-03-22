package com.contentgrid.gateway.runtime.servicediscovery;

import org.springframework.cloud.client.ServiceInstance;

public interface ServiceDeletedHandler {
    void handleServiceDeleted(ServiceInstance service);
}
