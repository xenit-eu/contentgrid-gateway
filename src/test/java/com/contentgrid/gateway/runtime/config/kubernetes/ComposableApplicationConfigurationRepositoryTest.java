package com.contentgrid.gateway.runtime.config.kubernetes;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.collections.ObservableMap.MapUpdate;
import com.contentgrid.gateway.collections.ObservableMap.UpdateType;
import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration.Keys;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationFragment;
import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ComposableApplicationConfigurationRepositoryTest {

    static final ApplicationId APP_ID = ApplicationId.random();

    @Test
    void getApplicationConfiguration() {
        var configs = new ComposableApplicationConfigurationRepository();

        configs.merge(new ApplicationConfigurationFragment("config-id", APP_ID, Map.of("foo", "bar")));

        var appConfig = configs.getApplicationConfiguration(APP_ID);
        assertThat(appConfig).isNotNull();
        assertThat(appConfig.getApplicationId()).isEqualTo(APP_ID);
        assertThat(appConfig.stream()).containsExactly(entry("foo", "bar"));
    }


    @Test
    void observe() {
        var configs = new ComposableApplicationConfigurationRepository();
        var appConfig1 = new ApplicationConfigurationFragment("config-id", ApplicationId.random(), Map.of("foo", "bar"));
        var appConfig2 = new ApplicationConfigurationFragment("config-id", ApplicationId.random(), Map.of("bar", "baz"));

        StepVerifier.create(configs.observe())

                // test put()
                .then(() -> {
                    configs.merge(appConfig1);
                    configs.merge(appConfig2);
                })
                .assertNext(next -> {
                    assertThat(next.getKey()).isEqualTo(appConfig1.getApplicationId());
                    assertThat(next.getType()).isEqualTo(UpdateType.PUT);
                    assertThat(next.getValue().stream()).singleElement().isEqualTo(entry("foo", "bar"));
                })
                .assertNext(next -> {
                    assertThat(next.getKey()).isEqualTo(appConfig2.getApplicationId());
                    assertThat(next.getType()).isEqualTo(UpdateType.PUT);
                    assertThat(next.getValue().stream()).singleElement().isEqualTo(entry("bar", "baz"));
                })

                // test remove();
                .then(() -> configs.remove(appConfig2.getApplicationId()))
                .assertNext(next -> {
                    assertThat(next.getType()).isEqualTo(UpdateType.REMOVE);
                    assertThat(next.getKey()).isEqualTo(appConfig2.getApplicationId());
                })

                // test clear()
                .then(configs::clear)
                .expectNext(MapUpdate.clear())

                .thenCancel()
                .verify();
    }

    @Test
    void getCors_fromMultipleFragments() {
        var appId = ApplicationId.random();

        var configs = new ComposableApplicationConfigurationRepository()
                .merge(fragment("config-console",
                        Map.of(Keys.CORS_ORIGINS, "https://manual.portal.example, https://test.example")))
                .merge(fragment("config-webapp", Map.of(Keys.CORS_ORIGINS, "https://webapp.contentgrid.app")))
                .merge(fragment("config-missing", Map.of()))
                .merge(fragment("config-blank", Map.of(Keys.CORS_ORIGINS, "")));

        var config = configs.getApplicationConfiguration(APP_ID);
        assertThat(config).isNotNull();
        assertThat(config.getCorsOrigins())
                .containsExactlyInAnyOrder(
                        "https://manual.portal.example",
                        "https://test.example",
                        "https://webapp.contentgrid.app");

    }

    private ApplicationConfigurationFragment fragment(String configId, Map<String, String> properties) {
        return new ApplicationConfigurationFragment(configId, APP_ID, properties);
    }
}