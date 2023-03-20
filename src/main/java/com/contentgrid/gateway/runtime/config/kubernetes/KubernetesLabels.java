package com.contentgrid.gateway.runtime.config.kubernetes;

import lombok.experimental.UtilityClass;

@UtilityClass
class KubernetesLabels {

    String K8S_MANAGEDBY = "app.kubernetes.io/managed-by";
    String CONTENTGRID_SERVICETYPE = "app.contentgrid.com/service-type";
    String CONTENTGRID_APPID = "app.contentgrid.com/application-id";
}
