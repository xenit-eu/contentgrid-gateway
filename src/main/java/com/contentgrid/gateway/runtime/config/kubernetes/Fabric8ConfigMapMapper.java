package com.contentgrid.gateway.runtime.config.kubernetes;

import com.contentgrid.gateway.runtime.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationFragment;
import io.fabric8.kubernetes.api.model.ConfigMap;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Fabric8ConfigMapMapper implements KubernetesResourceMapper<ConfigMap> {

    private static final String APP_ID_LABEL = "app.contentgrid.com/application-id";

    @Override
    public Optional<ApplicationConfigurationFragment> apply(ConfigMap configMap) {
        var fragmentId = configMap.getMetadata().getUid();

        var labels = configMap.getMetadata().getLabels();
        var fragment = Optional.ofNullable(labels.get(APP_ID_LABEL))
                .flatMap(ApplicationId::from)
                .map(appId -> new ApplicationConfigurationFragment(fragmentId, appId, configMap.getData()));

        if (fragment.isEmpty()) {
            log.warn("ConfigMap {} has no valid label '{}': {}", configMap.getFullResourceName(), APP_ID_LABEL, configMap);
            return Optional.empty();
        }

        return fragment;
    }
}
