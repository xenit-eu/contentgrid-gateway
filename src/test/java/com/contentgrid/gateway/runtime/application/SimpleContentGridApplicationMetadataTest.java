package com.contentgrid.gateway.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SimpleContentGridApplicationMetadataTest {

    ContentGridApplicationMetadata applicationMetadata = new SimpleContentGridApplicationMetadata(
            new SimpleContentGridDeploymentMetadata()
    );

    @Test
    void getApplicationId() {
        var appId = ApplicationId.random();
        var service = ServiceInstanceStubs.serviceInstance(appId);

        assertThat(applicationMetadata.getApplicationId(service)).hasValue(appId.toString());

    }

    @Test
    void getDomainNames() {
        var appId = ApplicationId.random();
        var service = ServiceInstanceStubs.serviceInstance(appId);

        assertThat(applicationMetadata.getDomainNames(service))
                .singleElement()
                .isEqualTo("%s.userapps.contentgrid.com".formatted(appId));
    }
}