package com.contentgrid.gateway.runtime.config;

import com.contentgrid.configuration.api.fragments.ComposedConfigurationRepository;
import com.contentgrid.configuration.applications.ApplicationConfiguration;
import com.contentgrid.configuration.applications.ApplicationId;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;

@RequiredArgsConstructor
public class ComposableApplicationConfigurationRepository implements ApplicationConfigurationRepository {

    private final ComposedConfigurationRepository<String, ApplicationId, ApplicationConfiguration> composedConfiguration;

    @Override
    @Nullable
    public ApplicationConfiguration getApplicationConfiguration(@NonNull ApplicationId appId) {
        return composedConfiguration.findConfiguration(appId).getConfiguration().orElse(null);
    }

    @Override
    public Stream<ApplicationId> applicationIds() {
        return this.composedConfiguration.compositionKeys();
    }
}
