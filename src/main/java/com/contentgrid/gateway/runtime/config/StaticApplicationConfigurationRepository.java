package com.contentgrid.gateway.runtime.config;

import com.contentgrid.configuration.applications.ApplicationConfiguration;
import com.contentgrid.configuration.applications.ApplicationId;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StaticApplicationConfigurationRepository implements ApplicationConfigurationRepository {

    private final Map<ApplicationId, ApplicationConfiguration> configs;

    @Override
    public ApplicationConfiguration getApplicationConfiguration(ApplicationId appId) {
        return configs.get(appId);
    }

    @Override
    public Stream<ApplicationId> applicationIds() {
        return configs.keySet().stream();
    }
}
