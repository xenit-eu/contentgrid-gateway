package com.contentgrid.gateway.runtime.config.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.ApplicationId;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class Fabric8ConfigMapMapperTest {

    @Test
    void validConfig() {
        var mapper = new Fabric8ConfigMapMapper();
        var fragment = mapper.apply(Fixtures.configMap()).orElseThrow();

        assertThat(fragment.getApplicationId())
                .isEqualTo(ApplicationId.from("f2c9cbef-dd86-4625-a805-28b65dd068cf").orElseThrow());
        assertThat(fragment.getProperty("contentgrid.routing.domains"))
                .hasValue("f2c9cbef-dd86-4625-a805-28b65dd068cf.userapps.contentgrid.com");
    }

    @Test
    void missingAppIdLabel() {
        var mapper = new Fabric8ConfigMapMapper();
        var configMap = Fixtures.configMap();
        configMap.getMetadata().getLabels().remove(KubernetesLabels.CONTENTGRID_APPID);

        var fragment = mapper.apply(configMap);

        assertThat(fragment).isEmpty();
    }

    private static class Fixtures {

        private static final ObjectMapper jackson = new ObjectMapper();


        @SneakyThrows
        private static ConfigMap configMap() {
            return jackson.readValue("""
                    {
                      "apiVersion": "v1",
                      "data": {
                        "contentgrid.routing.domains": "f2c9cbef-dd86-4625-a805-28b65dd068cf.userapps.contentgrid.com"
                      },
                      "kind": "ConfigMap",
                      "metadata": {
                        "creationTimestamp": "2023-03-20T10:35:30Z",
                        "labels": {
                          "app.contentgrid.com/application-id": "f2c9cbef-dd86-4625-a805-28b65dd068cf",
                          "app.contentgrid.com/service-type": "gateway",
                          "app.kubernetes.io/managed-by": "contentgrid",
                          "captain.contentgrid.com/resource-id": "a9f88853-8321-4915-8bf7-dc2e99e27a4a"
                        },
                        "name": "a9f88853-8321-4915-8bf7-dc2e99e27a4a-gw",
                        "namespace": "default",
                        "resourceVersion": "2269133190",
                        "uid": "371dc064-ea0a-4bdf-8e3d-a32147956318"
                      }
                    }
                    """, ConfigMap.class);
        }
    }
}