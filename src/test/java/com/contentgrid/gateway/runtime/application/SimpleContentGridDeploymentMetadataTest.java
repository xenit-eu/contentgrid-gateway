package com.contentgrid.gateway.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.test.runtime.ServiceInstanceStubs;
import org.junit.jupiter.api.Test;

class SimpleContentGridDeploymentMetadataTest {

    SimpleContentGridDeploymentMetadata metadata = new SimpleContentGridDeploymentMetadata();

    @Test
    void testMetadata() {
        var deployId = DeploymentId.random();
        var appId = ApplicationId.random();
        var policy = ServiceInstanceStubs.randomPolicyPackage();
        var service = ServiceInstanceStubs.serviceInstance(deployId, appId, policy);

        assertThat(metadata.getDeploymentId(service)).hasValue(deployId);
        assertThat(metadata.getApplicationId(service)).hasValue(appId);
        assertThat(metadata.getPolicyPackage(service)).hasValue(policy);
    }


}