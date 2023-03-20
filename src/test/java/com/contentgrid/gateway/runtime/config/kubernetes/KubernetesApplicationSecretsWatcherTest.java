package com.contentgrid.gateway.runtime.config.kubernetes;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationFragment;
import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.config.kubernetes.KubernetesApplicationSecretsWatcher.GatewaySecretWatcher;
import com.contentgrid.gateway.runtime.config.kubernetes.KubernetesApplicationSecretsWatcher.KubernetesLabels;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Watcher.Action;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KubernetesApplicationSecretsWatcherTest {

    @Test
    void secret_added() {
        var configs = new ComposableApplicationConfigurationRepository();
        var watcher = new GatewaySecretWatcher(configs);

        var appId = ApplicationId.random();

        var encoder = Base64.getEncoder();
        var metadata = new ObjectMeta();
        metadata.setName("my-secret-name");
        metadata.setUid(UUID.randomUUID().toString());
        metadata.setLabels(Map.of(
                KubernetesLabels.CONTENTGRID_APPID, appId.toString(),
                KubernetesLabels.CONTENTGRID_SERVICETYPE, "gateway",
                KubernetesLabels.K8S_MANAGEDBY, "contentgrid"
        ));

        var secret = new Secret();
        secret.setMetadata(metadata);
        secret.setData(Map.of("foo", encoder.encodeToString("bar".getBytes())));

        watcher.eventReceived(Action.ADDED, secret);

        assertThat(configs.getApplicationConfiguration(appId))
                .isNotNull()
                .satisfies(appConfig -> {
                    assertThat(appConfig.getApplicationId()).isEqualTo(appId);
                    assertThat(appConfig.stream()).singleElement().isEqualTo(entry("foo", "bar"));
                });
    }

    @Test
    void secret_removed() {
        var configs = new ComposableApplicationConfigurationRepository();
        var watcher = new GatewaySecretWatcher(configs);

        var appId = ApplicationId.random();
        var fragmentId = UUID.randomUUID().toString();

        configs.merge(new ApplicationConfigurationFragment(fragmentId, appId, Map.of("foo", "bar")));

        var encoder = Base64.getEncoder();
        var metadata = new ObjectMeta();
        metadata.setName("my-secret-name");
        metadata.setUid(fragmentId);
        metadata.setLabels(Map.of(
                KubernetesLabels.CONTENTGRID_APPID, appId.toString(),
                KubernetesLabels.CONTENTGRID_SERVICETYPE, "gateway",
                KubernetesLabels.K8S_MANAGEDBY, "contentgrid"
        ));

        var secret = new Secret();
        secret.setMetadata(metadata);
        secret.setData(Map.of("foo", encoder.encodeToString("bar".getBytes())));

        // verify the starting condition
        assertThat(configs.getApplicationConfiguration(appId)).isNotNull();

        // process DELETED event of secret with UUID='fragmentId'
        watcher.eventReceived(Action.DELETED, secret);

        // verify config has been deleted
        assertThat(configs.getApplicationConfiguration(appId)).isNull();
    }
}