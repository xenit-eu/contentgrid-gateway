package eu.xenit.alfred.content.gateway.runtime.config;

import eu.xenit.alfred.content.gateway.runtime.ApplicationId;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ComposedApplicationConfiguration implements ApplicationConfiguration {

    @Getter
    @NonNull
    private final ApplicationId applicationId;

    @Override
    public String getConfigurationId() {
        return this.getApplicationId().toString();
    }

    private final Map<String, ApplicationConfigurationFragment> fragments = new HashMap<>();

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

    public void putFragment(@NonNull ApplicationConfigurationFragment fragment) {
        if (!Objects.equals(this.getApplicationId(), fragment.getApplicationId())) {
            var template = "Fragment app-id is %s; expected %s";
            var message = template.formatted(fragment.getApplicationId(), this.getApplicationId());
            throw new IllegalArgumentException(message);
        }

        this.fragments.put(fragment.getConfigurationId(), fragment);
    }


    public void removeFragment(String fragmentId) {
        this.fragments.remove(fragmentId);
    }
}
