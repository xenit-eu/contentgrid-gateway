package com.contentgrid.gateway.runtime.config;

import com.contentgrid.gateway.collections.ObservableMap;
import com.contentgrid.gateway.collections.ObservableMap.MapUpdate;
import com.contentgrid.gateway.runtime.application.ApplicationId;
import lombok.NonNull;
import lombok.Synchronized;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;

public class ComposableApplicationConfigurationRepository implements
        ApplicationConfigurationRepository {

    protected final ObservableMap<ApplicationId, ComposedApplicationConfiguration> configs = new ObservableMap<>();

    @Override
    @Nullable
    public ComposedApplicationConfiguration getApplicationConfiguration(@NonNull ApplicationId appId) {
        return configs.get(appId);
    }

    public ComposedApplicationConfiguration getOrDefault(@NonNull ApplicationId applicationId) {
        return this.configs.getOrDefault(applicationId, new ComposedApplicationConfiguration(applicationId));
    }


    @Synchronized
    public void put(ComposedApplicationConfiguration applicationConfig) {
        this.configs.put(applicationConfig.getApplicationId(), applicationConfig);
    }

    @Synchronized
    public void remove(@NonNull ApplicationId applicationId) {
        this.configs.remove(applicationId);
    }

    @Synchronized
    public void clear() {
        this.configs.clear();
    }

    public void merge(@NonNull ApplicationConfiguration fragment) {
        var merged = this.getOrDefault(fragment.getApplicationId()).withAdditionalConfiguration(fragment);
        this.put(merged);
    }

    public void revoke(@NonNull ApplicationConfiguration fragment) {
        var appConfig = this.getOrDefault(fragment.getApplicationId())
                .withoutConfiguration(fragment.getConfigurationId());

        if (appConfig.isEmpty()) {
            this.remove(fragment.getApplicationId());
        } else {
            this.put(appConfig);
        }
    }

    @Override
    public Flux<MapUpdate<ApplicationId, ApplicationConfiguration>> observe() {
        return this.configs.observe()
                .map(update -> new MapUpdate<>(update.getType(), update.getKey(), update.getValue()));
    }
}
