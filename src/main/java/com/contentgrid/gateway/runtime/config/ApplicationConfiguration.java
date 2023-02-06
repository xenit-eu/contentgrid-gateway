package com.contentgrid.gateway.runtime.config;

import com.contentgrid.gateway.runtime.ApplicationId;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.NonNull;

public interface ApplicationConfiguration {

    String getConfigurationId();

    ApplicationId getApplicationId();

    Optional<String> getProperty(@NonNull String property);

    Stream<String> keys();

    Stream<Entry<String, String>> stream();
}
