package com.contentgrid.gateway.runtime.config;

import com.contentgrid.gateway.collections.ObservableMap.MapUpdate;
import com.contentgrid.gateway.runtime.ApplicationId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ApplicationConfigurationRepository {

    Mono<ApplicationConfiguration> getApplicationConfiguration(ApplicationId appId);

    Flux<MapUpdate<ApplicationId, ApplicationConfiguration>> observe();

}
