package eu.xenit.alfred.content.gateway.runtime.config;

import eu.xenit.alfred.content.gateway.runtime.ApplicationId;
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
