package com.contentgrid.gateway.runtime.config;

import com.contentgrid.gateway.collections.ObservableMap.MapUpdate;
import com.contentgrid.gateway.runtime.ApplicationId;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;

public interface ApplicationConfigurationRepository {

    @Nullable
    ApplicationConfiguration getApplicationConfiguration(ApplicationId appId);

    Flux<MapUpdate<ApplicationId, ApplicationConfiguration>> observe();

}
