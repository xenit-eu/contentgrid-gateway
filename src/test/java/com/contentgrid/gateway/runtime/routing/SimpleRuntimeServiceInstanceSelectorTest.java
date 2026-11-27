package com.contentgrid.gateway.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.configuration.applications.ApplicationId;
import com.contentgrid.gateway.runtime.application.DeploymentId;
import com.contentgrid.gateway.runtime.application.SimpleContentGridDeploymentMetadata;
import com.contentgrid.gateway.runtime.config.kubernetes.KubernetesLabels;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class SimpleRuntimeServiceInstanceSelectorTest {

    private static final ApplicationId APP_ID = ApplicationId.random();

    private final RuntimeServiceInstanceSelector selector = new SimpleRuntimeServiceInstanceSelector(
            new SimpleContentGridDeploymentMetadata());

    private final MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("https://my-app.contentgrid.cloud/").build());

    @Test
    void selectsYoungestDeployment_regardlessOfDeploymentId() {
        var older = service(DeploymentId.from("00000000-0000-0000-0000-000000000000"), Instant.parse("2026-01-01T10:00:00Z"));
        var newer = service(DeploymentId.from("ffffffff-0000-0000-0000-000000000000"), Instant.parse("2026-01-01T11:00:00Z"));

        assertThat(selector.selectService(exchange, List.of(newer, older))).containsSame(newer);
        assertThat(selector.selectService(exchange, List.of(older, newer))).containsSame(newer);
    }

    @Test
    void selectsLowestDeploymentId_whenCreationTimestampsAreEqual() {
        var timestamp = Instant.parse("2026-01-01T10:00:00Z");
        var high = service(DeploymentId.from("ffffffff-0000-0000-0000-000000000000"), timestamp);
        var low = service(DeploymentId.from("00000000-0000-0000-0000-000000000000"), timestamp);

        assertThat(selector.selectService(exchange, List.of(high, low))).containsSame(low);
    }

    @Test
    void prefersServiceWithCreationTimestamp_overServiceWithout() {
        var withTimestamp = service(DeploymentId.from("ffffffff-0000-0000-0000-000000000000"), Instant.parse("2026-01-01T10:00:00Z"));
        var withoutTimestamp = service(DeploymentId.from("00000000-0000-0000-0000-000000000000"), null);

        assertThat(selector.selectService(exchange, List.of(withoutTimestamp, withTimestamp))).containsSame(withTimestamp);
    }

    @Test
    void selectsLowestDeploymentId_withoutCreationTimestamps() {
        var high = service(DeploymentId.from("ffffffff-0000-0000-0000-000000000000"), null);
        var low = service(DeploymentId.from("00000000-0000-0000-0000-000000000000"), null);

        assertThat(selector.selectService(exchange, List.of(high, low))).containsSame(low);
    }

    @Test
    void noCandidates_selectsNothing() {
        assertThat(selector.selectService(exchange, List.of())).isEmpty();
    }

    private static ServiceInstance service(DeploymentId deploymentId, Instant creationTimestamp) {
        var metadata = new HashMap<String, String>();
        metadata.put(KubernetesLabels.CONTENTGRID_APPID, APP_ID.toString());
        metadata.put(KubernetesLabels.CONTENTGRID_DEPLOYID, deploymentId.toString());
        if (creationTimestamp != null) {
            metadata.put(SimpleContentGridDeploymentMetadata.METADATA_CREATION_TIMESTAMP, creationTimestamp.toString());
        }
        return new DefaultServiceInstance(UUID.randomUUID().toString(), "api-d-" + deploymentId,
                "api-d-%s.default.svc.cluster.local".formatted(deploymentId), 8080, false, metadata);
    }
}
