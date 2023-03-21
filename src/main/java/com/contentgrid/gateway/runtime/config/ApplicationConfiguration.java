package com.contentgrid.gateway.runtime.config;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

public interface ApplicationConfiguration {

    @UtilityClass
    class Keys {

        public static final String CLIENT_ID = "contentgrid.idp.client-id";
        public static final String CLIENT_SECRET = "contentgrid.idp.client-secret";
        public static final String ISSUER_URI = "contentgrid.idp.issuer-uri";

        public static final String ROUTING_DOMAINS = "contentgrid.routing.domains";
    }

    String getConfigurationId();

    ApplicationId getApplicationId();

    Optional<String> getProperty(@NonNull String property);

    Stream<String> keys();

    Stream<Entry<String, String>> stream();

    default String getClientId() {
        return this.getProperty(Keys.CLIENT_ID).orElse(null);
    }

    default String getClientSecret() {
        return this.getProperty(Keys.CLIENT_SECRET).orElse(null);
    }

    default String getIssuerUri() {
        return this.getProperty(Keys.ISSUER_URI).orElse(null);
    }

    default Set<String> getDomains() {
        return Arrays.stream(this.getProperty(Keys.ROUTING_DOMAINS).orElse("").split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }
}
