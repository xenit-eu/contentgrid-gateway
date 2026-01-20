package com.contentgrid.gateway.runtime.config.kubernetes;

import lombok.experimental.UtilityClass;

@UtilityClass
public class KubernetesLabels {

    public static final String CONTENTGRID_SERVICETYPE = "app.contentgrid.com/service-type";
    public static final String CONTENTGRID_APPID = "app.contentgrid.com/application-id";
    public static final String CONTENTGRID_DEPLOYID = "app.contentgrid.com/deployment-id";
    public static final String CONTENTGRID_POLICYPACKAGE = "authz.contentgrid.com/policy-package";

}
