package com.contentgrid.gateway.runtime.config;

import com.contentgrid.gateway.collections.ObservableMap;
import com.contentgrid.gateway.collections.ObservableMap.MapUpdate;
import com.contentgrid.gateway.runtime.application.ApplicationId;
import java.util.Collection;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class StaticApplicationConfigurationRepository implements ApplicationConfigurationRepository {

    private final ObservableMap<ApplicationId, ComposedApplicationConfiguration> configs = new ObservableMap<>();

    public StaticApplicationConfigurationRepository(Collection<ApplicationConfiguration> configurations) {
        for (ApplicationConfiguration configuration : configurations) {
            configs.computeIfAbsent(configuration.getApplicationId(), ComposedApplicationConfiguration::new);
            configs.computeIfPresent(configuration.getApplicationId(), (applicationId, existingConfiguration) -> existingConfiguration.withAdditionalConfiguration(configuration));
        }
    }

    @Override
    public ApplicationConfiguration getApplicationConfiguration(ApplicationId appId) {
        return configs.get(appId);
    }

    @Override
    public Flux<MapUpdate<ApplicationId, ApplicationConfiguration>> observe() {
        return configs.observe()
                .map(update -> new MapUpdate<>(update.getType(), update.getKey(), update.getValue()));
    }

    @Override
    public Stream<ApplicationId> applicationIds() {
        return configs.keySet().stream();
    }

    @Override
    public void clear() {

    }
}
