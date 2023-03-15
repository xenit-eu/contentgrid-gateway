package com.contentgrid.gateway.runtime.config.kubernetes;

import com.contentgrid.gateway.collections.ObservableMap;
import com.contentgrid.gateway.collections.ObservableMap.MapUpdate;
import com.contentgrid.gateway.runtime.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import lombok.NonNull;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;

public class BaseApplicationConfigurationRepository<T extends ApplicationConfiguration> implements
        ApplicationConfigurationRepository {

    protected final ObservableMap<ApplicationId, T> configs = new ObservableMap<>();

    @Override
    @Nullable
    public T getApplicationConfiguration(@NonNull ApplicationId appId) {
        return configs.get(appId);
    }

    public void put(T applicationConfig) {
        this.configs.put(applicationConfig.getApplicationId(), applicationConfig);
    }

    public void remove(ApplicationId applicationId) {
        this.configs.remove(applicationId);
    }

    public void clear() {
        this.configs.clear();
    }

    @Override
    public Flux<MapUpdate<ApplicationId, ApplicationConfiguration>> observe() {
        return this.configs.observe()
                .map(update -> new MapUpdate<>(update.getType(), update.getKey(), update.getValue()));
    }
}
