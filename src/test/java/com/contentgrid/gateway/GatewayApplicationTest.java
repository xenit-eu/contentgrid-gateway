package com.contentgrid.gateway;

import com.contentgrid.gateway.runtime.config.kubernetes.KubernetesResourceWatcherBinding;
import com.contentgrid.gateway.servicediscovery.KubernetesServiceDiscovery;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;


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
    class RuntimeWithKubernetes {

        @TestConfiguration
        static class TestConfig {

            @Bean
            @Primary
            KubernetesResourceWatcherBinding dummyKubernetesResourceWatcherBinding() {
                return Mockito.mock(KubernetesResourceWatcherBinding.class);
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