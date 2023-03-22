package com.contentgrid.gateway;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


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