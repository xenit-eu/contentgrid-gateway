package com.contentgrid.gateway.runtime.config.kubernetes;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.collections.ObservableMap.MapUpdate;
import com.contentgrid.gateway.collections.ObservableMap.UpdateType;
import com.contentgrid.gateway.runtime.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationFragment;
import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ComposableApplicationConfigurationRepositoryTest {

    @Test
    void getApplicationConfiguration() {
        var configs = new ComposableApplicationConfigurationRepository();
        var appId = ApplicationId.random();

        configs.merge(new ApplicationConfigurationFragment("config-id", appId, Map.of("foo", "bar")));

        var appConfig = configs.getApplicationConfiguration(appId);
        assertThat(appConfig).isNotNull();
        assertThat(appConfig.getApplicationId()).isEqualTo(appId);
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
}