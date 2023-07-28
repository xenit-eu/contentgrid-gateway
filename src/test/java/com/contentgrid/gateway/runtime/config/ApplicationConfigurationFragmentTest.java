package com.contentgrid.gateway.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfiguration.Keys;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApplicationConfigurationFragmentTest {

    ApplicationConfiguration SIMPLE = ApplicationConfigurationFragment.fromProperties(Map.of(
            Keys.ROUTING_DOMAINS, "my-app.contentgrid.cloud",
            Keys.CORS_ORIGINS, "https://my-app.contentgrid.app"
    ));

    ApplicationConfiguration MESSY = ApplicationConfigurationFragment.fromProperties(Map.of(
            Keys.ROUTING_DOMAINS, ",foo.bar.test,my-app.contentgrid.cloud ;;,;      other.domain.test,",
            Keys.CORS_ORIGINS, ";https://my-app.contentgrid.app; http://localhost:* , https://*.domain.test"
    ));

    ApplicationConfiguration EMPTY = ApplicationConfigurationFragment.fromProperties(Map.of(
            Keys.ROUTING_DOMAINS, ";;",
            Keys.CORS_ORIGINS, ""
    ));

    ApplicationConfiguration MISSING = ApplicationConfigurationFragment.fromProperties(Map.of(
    ));


    @Test
    void getRoutingDomains() {
        assertThat(SIMPLE.getDomains()).singleElement().isEqualTo("my-app.contentgrid.cloud");
        assertThat(MESSY.getDomains())
                .containsExactlyInAnyOrder("foo.bar.test", "my-app.contentgrid.cloud", "other.domain.test");
        assertThat(EMPTY.getDomains()).isNotNull().isEmpty();
        assertThat(MISSING.getDomains()).isNotNull().isEmpty();
    }

    @Test
    void getCorsOrigins() {
        assertThat(SIMPLE.getCorsOrigins()).singleElement().isEqualTo("https://my-app.contentgrid.app");
        assertThat(MESSY.getCorsOrigins()).containsExactlyInAnyOrder(
                "https://my-app.contentgrid.app", "http://localhost:*", "https://*.domain.test");
        assertThat(EMPTY.getCorsOrigins()).isNotNull().isEmpty();
        assertThat(MISSING.getCorsOrigins()).isNotNull().isEmpty();
    }

    @Test
    void testEquals() {
        var configId = SIMPLE.getConfigurationId();
        var appId = SIMPLE.getApplicationId();

        var props1 = new LinkedHashMap<String, String>();
        props1.put(Keys.ROUTING_DOMAINS, "my-app.contentgrid.cloud");
        props1.put(Keys.CORS_ORIGINS, "https://my-app.contentgrid.app");

        var props2 = new LinkedHashMap<String, String>();
        props2.put(Keys.CORS_ORIGINS, "https://my-app.contentgrid.app");
        props2.put(Keys.ROUTING_DOMAINS, "my-app.contentgrid.cloud");

        assertThat(SIMPLE).isEqualTo(new ApplicationConfigurationFragment(configId, appId, props1));
        assertThat(SIMPLE).isEqualTo(new ApplicationConfigurationFragment(configId, appId, props2));

        assertThat(new ApplicationConfigurationFragment(configId, appId, props1)).isEqualTo(SIMPLE);
        assertThat(new ApplicationConfigurationFragment(configId, appId, props2)).isEqualTo(SIMPLE);

        assertThat(new ApplicationConfigurationFragment(configId, appId, props1))
                .isEqualTo(new ApplicationConfigurationFragment(configId, appId, props2));
    }

}