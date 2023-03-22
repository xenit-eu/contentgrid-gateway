package com.contentgrid.gateway.runtime.config.kubernetes;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationFragment;
import io.fabric8.kubernetes.api.model.ConfigMap;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Fabric8ConfigMapMapper implements KubernetesResourceMapper<ConfigMap> {

    @Override
    public Optional<ApplicationConfigurationFragment> apply(ConfigMap configMap) {
        var fragmentId = configMap.getMetadata().getUid();
        var appIdLabel = configMap.getMetadata().getLabels().get(KubernetesLabels.CONTENTGRID_APPID);

        return Optional.ofNullable(appIdLabel)
                .flatMap(ApplicationId::from)
                .map(appId -> new ApplicationConfigurationFragment(fragmentId, appId, configMap.getData()))
                .or(() -> {
                    log.warn("{} {} has no valid label '{}'",
                            configMap.getFullResourceName(), configMap.getMetadata().getName(), KubernetesLabels.CONTENTGRID_APPID);
                    return Optional.empty();
                });
    }
}
