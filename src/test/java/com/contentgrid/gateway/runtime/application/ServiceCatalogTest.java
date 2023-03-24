package com.contentgrid.gateway.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.ServiceInstanceStubs;
import org.junit.jupiter.api.Test;

class ServiceCatalogTest {

    ContentGridDeploymentMetadata deploymentMetadata = new SimpleContentGridDeploymentMetadata();
    ContentGridApplicationMetadata applicationMetadata = new SimpleContentGridApplicationMetadata(deploymentMetadata);

    @Test
    void services() {
        var catalog = new ServiceCatalog(event -> {}, deploymentMetadata, applicationMetadata);

        var appId1 = ApplicationId.random();
        var deploy1 = DeploymentId.random();

        var service1 = ServiceInstanceStubs.serviceInstance(deploy1, appId1);
        catalog.handleServiceAdded(service1);
        assertThat(catalog.services()).singleElement();

        var deploy2 = DeploymentId.random();
        catalog.handleServiceAdded(ServiceInstanceStubs.serviceInstance(deploy2, appId1));
        assertThat(catalog.services()).hasSize(2);
        assertThat(catalog.findByApplicationId(appId1)).hasSize(2);

        catalog.handleServiceDeleted(service1);
        assertThat(catalog.services()).hasSize(1);
        assertThat(catalog.findByApplicationId(appId1)).hasSize(1);
    }

    @Test
    void findByApplicationId_empty() {
        var catalog = new ServiceCatalog(event -> {}, deploymentMetadata, applicationMetadata);

        var result = catalog.findByApplicationId(ApplicationId.random());
        assertThat(result).isEmpty();
    }
}