package com.contentgrid.gateway.runtime.config;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.Value;

@Value
public class ApplicationConfigurationFragment implements ApplicationConfiguration {

    @NonNull
    String configurationId;

    @NonNull
    ApplicationId applicationId;

    @NonNull
    Map<String, String> properties;

    public Optional<String> getProperty(@NonNull String property) {
        return Optional.ofNullable(this.properties.get(property));
    }

    @Override
    public Stream<String> keys() {
        return this.properties.keySet().stream();
    }

    @Override
    public Stream<Entry<String, String>> stream() {
        return this.properties.entrySet().stream();
    }

}
