package com.contentgrid.gateway.runtime.config.kubernetes;

import com.contentgrid.gateway.runtime.application.ApplicationId;
import com.contentgrid.gateway.runtime.config.ApplicationConfigurationFragment;
import io.fabric8.kubernetes.api.model.Secret;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Fabric8SecretMapper implements KubernetesResourceMapper<Secret> {

    private static final String APP_ID_LABEL = "app.contentgrid.com/application-id";

    @Override
    public Optional<ApplicationConfigurationFragment> apply(Secret secret) {
        var fragmentId = secret.getMetadata().getUid();

        var labels = secret.getMetadata().getLabels();
        var fragment = Optional.ofNullable(labels.get(APP_ID_LABEL))
                .flatMap(ApplicationId::from)
                .map(appId -> new ApplicationConfigurationFragment(fragmentId, appId, base64decode(secret.getData())));

        if (fragment.isEmpty()) {
            log.warn("Secret {} has no valid label '{}': {}", secret.getFullResourceName(), APP_ID_LABEL, secret);
            return Optional.empty();
        }

        return fragment;
    }

    private Map<String, String> base64decode(Map<String, String> data) {
        var decoder = Base64.getDecoder();
        var result = new HashMap<String, String>();
        data.forEach((key, value) -> result.put(key, new String(decoder.decode(value))));

        return result;
    }

}
