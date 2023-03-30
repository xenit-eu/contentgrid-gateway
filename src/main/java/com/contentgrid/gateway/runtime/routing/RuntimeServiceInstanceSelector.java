package com.contentgrid.gateway.runtime.routing;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import java.util.Collection;
import java.util.Optional;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.server.ServerWebExchange;

/**
 * Strategy interface that is responsible to select the appropriate {@link ServiceInstance} for a given
 * {@link ServerWebExchange} and {@link ApplicationId}
 */
public interface RuntimeServiceInstanceSelector {

    Optional<ServiceInstance> selectService(ServerWebExchange serverWebExchange, Collection<ServiceInstance> services);

}
