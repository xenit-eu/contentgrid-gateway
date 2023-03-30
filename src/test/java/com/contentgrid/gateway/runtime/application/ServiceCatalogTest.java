package com.contentgrid.gateway.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.ServiceInstanceStubs;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ServiceCatalogTest {

    ContentGridDeploymentMetadata deploymentMetadata = new SimpleContentGridDeploymentMetadata();
    ApplicationConfigurationRepository appConfigRepo = Mockito.mock(ApplicationConfigurationRepository.class);

    @Test
    void services() {
        var catalog = new ServiceCatalog(event -> {}, deploymentMetadata, appConfigRepo);

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
        var catalog = new ServiceCatalog(event -> {}, deploymentMetadata, appConfigRepo);

        var result = catalog.findByApplicationId(ApplicationId.random());
        assertThat(result).isEmpty();
    }
}