package com.contentgrid.gateway.runtime.config.kubernetes;

import com.contentgrid.gateway.collections.ObservableMap;
import com.contentgrid.gateway.collections.ObservableMap.MapUpdate;
import com.contentgrid.gateway.runtime.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class BaseApplicationConfigurationRepository<T extends ApplicationConfiguration> implements ApplicationConfigurationRepository {

    protected final ObservableMap<ApplicationId, T> configs = new ObservableMap<>();

    @Override
    public Mono<ApplicationConfiguration> getApplicationConfiguration(@NonNull ApplicationId appId) {
        return Mono.fromSupplier(() -> configs.get(appId));
    }

    @Override
    public Flux<MapUpdate<ApplicationId, ApplicationConfiguration>> observe() {
        return this.configs.observe()
                .map(update -> new MapUpdate<>(update.getType(), update.getKey(), update.getValue()));
    }
}
