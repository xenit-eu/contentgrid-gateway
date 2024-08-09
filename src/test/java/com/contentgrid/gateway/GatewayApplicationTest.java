package com.contentgrid.gateway;

import static io.fabric8.kubernetes.client.Config.fromKubeconfig;

import com.contentgrid.gateway.runtime.servicediscovery.KubernetesServiceDiscovery;
import io.fabric8.kubernetes.client.Config;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;


class GatewayApplicationTest {

    @Nested
    @SpringBootTest
    class DefaultConfiguration {

        @Test
        void contextLoads() {

        }
    }

    @Nested
    @SpringBootTest(properties = {
            "contentgrid.gateway.runtime-platform.enabled=true",
            "servicediscovery.enabled=true",
            "spring.main.cloud-platform=kubernetes"
    })
    @Testcontainers
    class RuntimeWithKubernetes {
        @Container
        private static final K3sContainer K8S = new K3sContainer(DockerImageName.parse("rancher/k3s:latest"));

        @TestConfiguration
        static class TestConfig {

            @Bean
            Config kubernetesConfig() {
                return fromKubeconfig(K8S.getKubeConfigYaml());
            }

            @Bean
            @Primary
            KubernetesServiceDiscovery dummyKubernetesServiceDiscovery() {
                return Mockito.mock(KubernetesServiceDiscovery.class);
            }
        }

        @Test
        void contextLoads() {

        }
    }

    @Nested
    @SpringBootTest(properties = {
            "contentgrid.gateway.runtime-platform.enabled=true",
            "servicediscovery.enabled=true",
            "spring.main.cloud-platform=none"
    })
    class RuntimeNoCloudPlatform {
        @Test
        void contextLoads() {

        }
    }
}