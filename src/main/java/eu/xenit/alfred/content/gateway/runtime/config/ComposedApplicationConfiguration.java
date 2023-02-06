package eu.xenit.alfred.content.gateway.runtime.config;

import eu.xenit.alfred.content.gateway.runtime.ApplicationId;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Immutable {@link ApplicationConfiguration} that allows to composition from multiple sources.
 */
@Slf4j
public class ComposedApplicationConfiguration implements ApplicationConfiguration {

    @Getter
    @NonNull
    private final ApplicationId applicationId;

    public ComposedApplicationConfiguration(@NonNull ApplicationId applicationId) {
        this(applicationId, List.of());
    }

    public ComposedApplicationConfiguration(
            @NonNull ApplicationId applicationId,
            @NonNull Collection<ApplicationConfiguration> fragments) {
        this.applicationId = applicationId;
        this.fragments = Collections.unmodifiableMap(fragments.stream()
                .map(Objects::requireNonNull)
                .peek(fragment -> {
                    if (!applicationId.equals(fragment.getApplicationId())) {
                        String msg = "Fragment app-id is %s; expected %s"
                                .formatted(fragment.getApplicationId(), applicationId);
                        throw new IllegalArgumentException(msg);
                    }
                })
                .collect(Collectors.toMap(ApplicationConfiguration::getConfigurationId, Function.identity())));
    }


    @Override
    public String getConfigurationId() {
        return this.getApplicationId().toString();
    }

    private final Map<String, ApplicationConfiguration> fragments;

    @Override
    public Optional<String> getProperty(@NonNull String property) {
        return this.fragments.values().stream()
                .flatMap(frag -> frag.getProperty(property).stream())
                .findFirst();
    }

    public Stream<Entry<String, String>> stream() {
        return this.keys().flatMap(key -> this.getProperty(key).stream().map(prop -> Map.entry(key, prop)));
    }

    public Stream<String> keys() {
        return this.fragments.values().stream().flatMap(ApplicationConfiguration::keys).distinct();
    }

    /**
     * Returns a new instance of {@link ComposedApplicationConfiguration}, including the provided
     * {@link ApplicationConfiguration} fragment.
     *
     * @param configurationFragment the configuration to add
     * @return a new instance of {@link ComposedApplicationConfiguration}
     */
    public ComposedApplicationConfiguration withAdditionalConfiguration(@NonNull ApplicationConfiguration configurationFragment) {
        if (!Objects.equals(this.getApplicationId(), configurationFragment.getApplicationId())) {
            var template = "Fragment application-id is %s; expected %s";
            var message = template.formatted(configurationFragment.getApplicationId(), this.getApplicationId());
            throw new IllegalArgumentException(message);
        }

        var result = new LinkedHashMap<>(this.fragments);
        result.put(configurationFragment.getConfigurationId(), configurationFragment);

        return new ComposedApplicationConfiguration(this.getApplicationId(), result.values());
    }


    /**
     * Returns a new instance of {@link ComposedApplicationConfiguration}, with the
     * {@link ApplicationConfiguration} fragment referenced by id removed, or this instance when an
     * ApplicationConfiguration with that id is not found.
     *
     * @param configurationFragmentId the configuration-id to be removed
     * @return a new instance of {@link ComposedApplicationConfiguration}
     */
    public ComposedApplicationConfiguration withoutConfiguration(String configurationFragmentId) {
        var result = new LinkedHashMap<>(this.fragments);
        var removed = result.remove(configurationFragmentId);
        if (removed == null) {
            return this;
        }

        return new ComposedApplicationConfiguration(this.getApplicationId(), result.values());
    }

    public boolean isEmpty() {
        return this.fragments.isEmpty();
    }
}
