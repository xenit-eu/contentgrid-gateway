package com.contentgrid.gateway.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.ServiceInstanceStubs;
import org.junit.jupiter.api.Test;

class SimpleContentGridApplicationMetadataTest {

    ContentGridApplicationMetadata applicationMetadata = new SimpleContentGridApplicationMetadata(
            new SimpleContentGridDeploymentMetadata()
    );

    @Test
    void getApplicationId() {
        var appId = ApplicationId.random();
        var service = ServiceInstanceStubs.serviceInstance(appId);

        assertThat(applicationMetadata.getApplicationId(service)).hasValue(appId);

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