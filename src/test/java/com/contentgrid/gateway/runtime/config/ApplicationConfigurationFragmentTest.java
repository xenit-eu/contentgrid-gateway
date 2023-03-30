package com.contentgrid.gateway.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.config.ApplicationConfiguration.Keys;
import java.util.Map;
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
    void getRotingDomains() {
        assertThat(SIMPLE.getDomains()).singleElement().isEqualTo("my-app.contentgrid.cloud");
        assertThat(MESSY.getDomains())
                .containsExactlyInAnyOrder("foo.bar.test", "my-app.contentgrid.cloud", "other.domain.test");
        assertThat(EMPTY.getDomains()).isNotNull().isEmpty();
        assertThat(MISSING.getDomains()).isNotNull().isEmpty();
    }

    @Test
    void getCorsOrigins() {
        assertThat(SIMPLE.getDomains()).singleElement().isEqualTo("https://my-app.contentgrid.app");
        assertThat(MESSY.getCorsOrigins()).containsExactlyInAnyOrder(
                "https://my-app.contentgrid.app", "http://localhost:*", "https://*.domain.test");
        assertThat(EMPTY.getDomains()).isNotNull().isEmpty();
        assertThat(MISSING.getDomains()).isNotNull().isEmpty();
    }

}