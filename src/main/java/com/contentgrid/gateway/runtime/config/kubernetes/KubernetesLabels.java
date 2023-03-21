package com.contentgrid.gateway.runtime.config.kubernetes;

import lombok.experimental.UtilityClass;

@UtilityClass
class KubernetesLabels {

    static final String K8S_MANAGEDBY = "app.kubernetes.io/managed-by";
    static final String CONTENTGRID_SERVICETYPE = "app.contentgrid.com/service-type";
    static final String CONTENTGRID_APPID = "app.contentgrid.com/application-id";
}
