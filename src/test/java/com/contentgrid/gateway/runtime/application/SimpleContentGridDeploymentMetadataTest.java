package com.contentgrid.gateway.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimpleContentGridDeploymentMetadataTest {

    SimpleContentGridDeploymentMetadata metadata = new SimpleContentGridDeploymentMetadata();

    @Test
    void testMetadata() {
        var deployId = DeploymentId.random();
        var appId = ApplicationId.random();
        var policy = ServiceInstanceStubs.randomPolicyPackage();
        var service = ServiceInstanceStubs.serviceInstance(deployId, appId, policy);

        assertThat(metadata.getDeploymentId(service)).hasValue(deployId.toString());
        assertThat(metadata.getApplicationId(service)).hasValue(appId.toString());
        assertThat(metadata.getPolicyPackage(service)).hasValue(policy);
    }


}