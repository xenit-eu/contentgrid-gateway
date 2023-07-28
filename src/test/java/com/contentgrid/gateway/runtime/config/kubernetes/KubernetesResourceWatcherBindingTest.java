package com.contentgrid.gateway.runtime.config.kubernetes;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationFragment;
import com.contentgrid.gateway.runtime.config.ComposableApplicationConfigurationRepository;
import com.contentgrid.gateway.runtime.config.kubernetes.KubernetesResourceWatcherBinding.ApplicationConfigResourceHandler;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class KubernetesResourceWatcherBindingTest {


    @Test
    void secret_added() {
        var configs = new ComposableApplicationConfigurationRepository();
        var handler = new ApplicationConfigResourceHandler<>(configs, new Fabric8SecretMapper());

        var appId = ApplicationId.random();
        var secret = createSecret(appId, UUID.randomUUID().toString());

        handler.onAdd(secret);

        assertThat(configs.getApplicationConfiguration(appId))
                .isNotNull()
                .satisfies(appConfig -> {
                    assertThat(appConfig.getApplicationId()).isEqualTo(appId);
                    assertThat(appConfig.stream()).singleElement().isEqualTo(entry("foo", "bar"));
                });
    }

    @Test
    void secret_updated() {
        var configs = new ComposableApplicationConfigurationRepository();
        var handler = new ApplicationConfigResourceHandler<>(configs, new Fabric8SecretMapper());

        var appId = ApplicationId.random();
        var fragmentId = UUID.randomUUID().toString();

        var oldSecret = createSecret(appId, fragmentId);
        handler.onAdd(oldSecret);

        // verify the starting condition
        assertThat(configs.getApplicationConfiguration(appId)).isNotNull();

        // create new secret with same app-id and fragment-id, but different properties
        handler.onUpdate(oldSecret, createSecret(appId, fragmentId, Map.of("foo", "baz")));

        // verify config has been updated
        assertThat(configs.getApplicationConfiguration(appId)).isNotNull();

        assertThat(configs.getApplicationConfiguration(appId))
                .isNotNull()
                .satisfies(appConfig -> {
                    assertThat(appConfig.getApplicationId()).isEqualTo(appId);
                    assertThat(appConfig.stream()).singleElement().isEqualTo(entry("foo", "baz"));
                });
    }

    @Test
    void secret_updated_no_changes() {
        var configs = Mockito.mock(ComposableApplicationConfigurationRepository.class);
        var handler = new ApplicationConfigResourceHandler<>(configs, new Fabric8SecretMapper());

        var appId = ApplicationId.random();
        var fragmentId = UUID.randomUUID().toString();

        var oldSecret = createSecret(appId, fragmentId);
        var newSecret = createSecret(appId, fragmentId);
        handler.onUpdate(oldSecret, newSecret);

        Mockito.verifyNoMoreInteractions(configs);
    }

    @Test
    void secret_removed() {
        var configs = new ComposableApplicationConfigurationRepository();
        var handler = new ApplicationConfigResourceHandler<>(configs, new Fabric8SecretMapper());

        var appId = ApplicationId.random();
        var fragmentId = UUID.randomUUID().toString();

        configs.merge(new ApplicationConfigurationFragment(fragmentId, appId, Map.of("foo", "bar")));

        var secret = createSecret(appId, fragmentId);

        // verify the starting condition
        assertThat(configs.getApplicationConfiguration(appId)).isNotNull();

        // process DELETED event of secret with UUID='fragmentId'
        handler.onDelete(secret, false);

        // verify config has been deleted
        assertThat(configs.getApplicationConfiguration(appId)).isNull();
    }

    @Test
    void secret_removed_resync() {
        var configs = new ComposableApplicationConfigurationRepository();
        var handler = new ApplicationConfigResourceHandler<>(configs, new Fabric8SecretMapper());

        var appId = ApplicationId.random();
        var fragmentId = UUID.randomUUID().toString();

        configs.merge(new ApplicationConfigurationFragment(fragmentId, appId, Map.of("foo", "bar")));

        var secret = createSecret(appId, fragmentId);

        // verify the starting condition
        assertThat(configs.getApplicationConfiguration(appId)).isNotNull();

        // process DELETED event of secret with UUID='fragmentId'
        handler.onDelete(secret, true);

        // verify config has been deleted
        assertThat(configs.getApplicationConfiguration(appId)).isNull();
    }

    @Test
    void secret_nothingChanged() {
        var configs = Mockito.mock(ComposableApplicationConfigurationRepository.class);
        var handler = new ApplicationConfigResourceHandler<>(configs, new Fabric8SecretMapper());

        // unknown events should be ignored
        handler.onNothing();

        Mockito.verifyNoMoreInteractions(configs);
    }

    @Test
    void onClose() {
        var binding = new KubernetesResourceWatcherBinding(
                new ComposableApplicationConfigurationRepository(),
                Mockito.mock(KubernetesClient.class),
                "default", 0);

        var informer = Mockito.mock(SharedIndexInformer.class);

        binding.informInternal(kubernetesClient ->  informer, secret -> Optional.empty());
        binding.close();

        Mockito.verify(informer).close();
    }


        @NonNull
    private static Secret createSecret(ApplicationId appId, String fragmentId) {
        return createSecret(appId, fragmentId, Map.of("foo", "bar"));
    }

    @NonNull
    private static Secret createSecret(ApplicationId appId, String fragmentId, Map<String, String> properties) {
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
        var data = properties.entrySet().stream()
                .map(entry -> entry(entry.getKey(), encoder.encodeToString(entry.getValue().getBytes(
                        StandardCharsets.UTF_8))))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        secret.setData(data);
        return secret;
    }

}