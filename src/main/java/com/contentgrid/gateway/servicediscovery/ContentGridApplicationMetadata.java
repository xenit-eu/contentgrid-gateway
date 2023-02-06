package com.contentgrid.gateway.servicediscovery;

import java.util.Optional;
import java.util.Set;
import lombok.NonNull;
import org.springframework.cloud.client.ServiceInstance;

public interface ContentGridApplicationMetadata {

    Optional<String> getApplicationId(ServiceInstance service);

    /**
     * @deprecated domainname data will most probably be backed by another object, not `ServiceInstance`
     * @param service
     * @return
     */
    @Deprecated
    Set<String> getDomainNames(@NonNull ServiceInstance service);
}