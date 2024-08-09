package com.contentgrid.gateway.runtime.config;

import com.contentgrid.configuration.applications.ApplicationConfiguration;
import com.contentgrid.configuration.applications.ApplicationId;
import java.util.stream.Stream;
import org.springframework.lang.Nullable;

public interface ApplicationConfigurationRepository {

    @Nullable
    ApplicationConfiguration getApplicationConfiguration(ApplicationId appId);

    Stream<ApplicationId> applicationIds();

}
